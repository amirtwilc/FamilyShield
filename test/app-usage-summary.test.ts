import { describe, expect, test } from 'vitest';
import { averageUsageMinutes, hasYesterdayUsageData } from '@/lib/app-usage-summary';

describe('app usage summary calculations', () => {
  test('averages only days that have usage data', () => {
    expect(averageUsageMinutes([
      { min: 0, hasData: false },
      { min: 90, hasData: true },
      { min: 0, hasData: false },
      { min: 30, hasData: true },
      { min: 0, hasData: false },
      { min: 0, hasData: false },
      { min: 0, hasData: false },
    ])).toBe(60);
  });

  test('returns zero average when the week has no usage data', () => {
    expect(averageUsageMinutes([
      { min: 0, hasData: false },
      { min: 0, hasData: false },
    ])).toBe(0);
  });

  test('uses row count to decide whether yesterday comparison is meaningful', () => {
    expect(hasYesterdayUsageData(0)).toBe(false);
    expect(hasYesterdayUsageData(1)).toBe(true);
  });
});
