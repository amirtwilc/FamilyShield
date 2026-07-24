export type UsageDayForAverage = {
  min: number;
  hasData?: boolean;
  dataPoints?: number;
};

export function usageDayHasData(day: UsageDayForAverage): boolean {
  return day.hasData ?? (day.dataPoints ?? 0) > 0;
}

export function averageUsageMinutes(days: UsageDayForAverage[]): number {
  const daysWithData = days.filter(usageDayHasData);
  if (daysWithData.length === 0) return 0;
  return Math.round(daysWithData.reduce((sum, day) => sum + day.min, 0) / daysWithData.length);
}

export function hasYesterdayUsageData(rowCount: number): boolean {
  return rowCount > 0;
}
