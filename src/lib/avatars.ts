export const CHILD_AVATARS = [
  'fox', 'panda', 'tiger', 'unicorn', 'bunny', 'koala',
  'monkey', 'frog', 'dog', 'cat', 'bear', 'penguin',
  'lion', 'duck', 'chick', 'hamster', 'mouse', 'pig',
  'cow', 'horse', 'wolf', 'owl', 'turtle', 'dolphin',
] as const;

export type ChildAvatar = (typeof CHILD_AVATARS)[number];

export function normalizeAvatar(value: string | null | undefined): ChildAvatar | null {
  return CHILD_AVATARS.includes(value as ChildAvatar) ? value as ChildAvatar : null;
}

export function nextAvailableAvatar(used: Array<string | null | undefined>, seed = ''): ChildAvatar {
  const usedSet = new Set(used.map(normalizeAvatar).filter((v): v is ChildAvatar => Boolean(v)));
  return CHILD_AVATARS.find((a) => !usedSet.has(a))
    ?? CHILD_AVATARS[Math.abs(seed.split('').reduce((sum, ch) => sum + ch.charCodeAt(0), 0)) % CHILD_AVATARS.length];
}
