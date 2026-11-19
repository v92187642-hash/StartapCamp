import { expect, test } from "vitest";
import { addDays, dateInZone, formatDay, timeInZone, zonedToInstant } from "./time";

test("a wall-clock time in the plan's zone becomes the right moment", () => {
  // Yekaterinburg is UTC+5 all year.
  expect(zonedToInstant("2026-10-09", "08:30", "Asia/Yekaterinburg").toISOString()).toBe("2026-10-09T03:30:00.000Z");
  // Just after midnight there is still the previous day in UTC.
  expect(zonedToInstant("2026-10-09", "00:15", "Asia/Yekaterinburg").toISOString()).toBe("2026-10-08T19:15:00.000Z");
  // A zone with daylight saving time, on both sides of the switch.
  expect(zonedToInstant("2026-07-01", "12:00", "Europe/Berlin").toISOString()).toBe("2026-07-01T10:00:00.000Z");
  expect(zonedToInstant("2026-12-01", "12:00", "Europe/Berlin").toISOString()).toBe("2026-12-01T11:00:00.000Z");
});

test("date and time are read in the plan's zone, not the browser's", () => {
  const instant = new Date("2026-10-08T20:30:00Z");
  expect(dateInZone(instant, "Asia/Yekaterinburg")).toBe("2026-10-09");
  expect(dateInZone(instant, "Europe/Kaliningrad")).toBe("2026-10-08");
  expect(timeInZone(instant, "Asia/Yekaterinburg")).toBe("01:30");
  expect(timeInZone("2026-10-09T03:00:00Z", "Asia/Yekaterinburg")).toBe("08:00");
});

test("days are added across month and year ends", () => {
  expect(addDays("2026-10-31", 1)).toBe("2026-11-01");
  expect(addDays("2027-01-01", -1)).toBe("2026-12-31");
});

test("a day is shown in Russian", () => {
  expect(formatDay("2026-10-09")).toBe("пятница, 9 октября");
});
