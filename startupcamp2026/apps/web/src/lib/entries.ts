import type { Schemas } from "../api/client";

/**
 * What the day view shows for an entry. "No mark" is not stored on the server:
 * it is a planned entry whose time has passed.
 */
export type EntryState = "planned" | "marked" | "no-mark";

export function entryState(entry: Schemas["Entry"], now: Date): EntryState {
  if (entry.status === "marked") return "marked";
  return new Date(entry.scheduledAt) < now ? "no-mark" : "planned";
}
