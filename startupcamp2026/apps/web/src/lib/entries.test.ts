import { expect, test } from "vitest";
import type { Schemas } from "../api/client";
import { entryState } from "./entries";

const NOW = new Date("2026-10-09T10:00:00Z");

function entry(fields: Partial<Schemas["Entry"]>): Schemas["Entry"] {
  return { id: "e", planId: "p", planItemId: "i", label: "Назначение 1", scheduledAt: "", status: "planned", ...fields };
}

test("an entry in the future is planned", () => {
  expect(entryState(entry({ scheduledAt: "2026-10-09T11:00:00Z" }), NOW)).toBe("planned");
});

test("a planned entry whose time has passed has no mark", () => {
  expect(entryState(entry({ scheduledAt: "2026-10-09T09:00:00Z" }), NOW)).toBe("no-mark");
});

test("a marked entry is marked whenever it was due", () => {
  const marked = { status: "marked", markedAt: "2026-10-09T09:05:00Z" } as const;
  expect(entryState(entry({ scheduledAt: "2026-10-09T09:00:00Z", ...marked }), NOW)).toBe("marked");
  expect(entryState(entry({ scheduledAt: "2026-10-09T11:00:00Z", ...marked }), NOW)).toBe("marked");
});
