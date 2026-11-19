// Dates and times in the plan's time zone, which may differ from the browser's.

/** Offset of the zone from UTC at that moment, in milliseconds. */
function zoneOffsetMs(timeZone: string, utcMs: number): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(new Date(utcMs));
  const get = (type: string) => Number(parts.find((part) => part.type === type)!.value);
  const wallClock = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"), get("second"));
  return wallClock - Math.floor(utcMs / 1000) * 1000;
}

/** The moment when clocks in the zone show this date ("2026-10-09") and time ("08:30"). */
export function zonedToInstant(date: string, time: string, timeZone: string): Date {
  const [year, month, day] = date.split("-").map(Number) as [number, number, number];
  const [hour, minute] = time.split(":").map(Number) as [number, number];
  const wallClock = Date.UTC(year, month - 1, day, hour, minute);
  // Two passes: the offset at the guessed moment can differ from the one at the result.
  let utc = wallClock - zoneOffsetMs(timeZone, wallClock);
  utc = wallClock - zoneOffsetMs(timeZone, utc);
  return new Date(utc);
}

/** Calendar date in the zone, as "2026-10-09". */
export function dateInZone(instant: Date, timeZone: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(
    instant,
  );
}

/** Clock time in the zone, as "08:30". */
export function timeInZone(instant: Date | string, timeZone: string): string {
  return new Intl.DateTimeFormat("ru-RU", { timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(
    new Date(instant),
  );
}

/** "2026-10-09" plus or minus days. */
export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** "четверг, 9 октября" */
export function formatDay(date: string): string {
  return new Intl.DateTimeFormat("ru-RU", { timeZone: "UTC", weekday: "long", day: "numeric", month: "long" }).format(
    new Date(`${date}T00:00:00Z`),
  );
}

/** "9 октября 2026 г." */
export function formatDate(date: string): string {
  return new Intl.DateTimeFormat("ru-RU", { timeZone: "UTC", day: "numeric", month: "long", year: "numeric" }).format(
    new Date(`${date}T00:00:00Z`),
  );
}
