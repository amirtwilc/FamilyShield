import { test, expect, type Page } from '@playwright/test';

// End-to-end client loop: parent registers, adds a child, generates a pairing
// code; the kid simulator pairs and sends a low battery + location; the parent
// refreshes and sees the device online, the battery pill, the location, and a
// low-battery alert. Drives both apps in separate browser contexts.

function uniqueEmail() {
  return `e2e_${Date.now()}_${Math.floor(Math.random() * 1e4)}@familyshield.test`;
}

test('parent + kid full loop', async ({ browser }) => {
  const parentCtx = await browser.newContext();
  const kidCtx = await browser.newContext();
  const parent: Page = await parentCtx.newPage();
  const kid: Page = await kidCtx.newPage();

  // --- Parent: register ---
  await parent.goto('/parent');
  await parent.locator('#email').fill(uniqueEmail());
  await parent.locator('#password').fill('SuperSecret123!');
  await parent.getByTestId('login-submit').click();
  await expect(parent.getByRole('heading', { name: 'Family dashboard' })).toBeVisible();

  // --- Parent: add a child + generate a pairing code ---
  await parent.getByTestId('child-name').fill('Mia');
  await parent.getByTestId('add-child').click();
  await expect(parent.getByTestId('child-detail')).toBeVisible();
  await parent.getByTestId('gen-code').click();
  const code = (await parent.getByTestId('pairing-code').textContent())?.trim() ?? '';
  expect(code).toMatch(/^\d{6}$/);

  // --- Kid: pair with the code ---
  await kid.goto('/kid');
  await kid.getByTestId('pair-disclosure').check();
  await kid.getByTestId('pair-code').fill(code);
  await kid.getByTestId('pair-submit').click();
  await expect(kid.getByTestId('send-location')).toBeVisible();

  // --- Kid: set low battery, send location + status ---
  await kid.getByTestId('sim-battery').fill('10');
  await kid.getByTestId('send-location').click();
  await expect(kid.getByTestId('sim-msg')).toHaveText('Location sent.');
  await kid.getByTestId('send-status').click();
  await expect(kid.getByTestId('sim-msg')).toHaveText('Status sent.');

  // --- Parent: refresh and verify the loop closed ---
  await parent.getByTestId('refresh').click();
  await expect(parent.getByTestId('status-pill')).toHaveText('Online');
  await expect(parent.getByTestId('battery-pill')).toContainText('10%');
  await expect(parent.getByTestId('loc-text')).toBeVisible();
  await expect(parent.getByTestId('alert-low_battery')).toBeVisible();

  await parentCtx.close();
  await kidCtx.close();
});
