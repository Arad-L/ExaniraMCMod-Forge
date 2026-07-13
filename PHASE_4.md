# Phase 4 Execution — Main Story System

**Goal**: Per-player story progression and season model stored in `CharacterSheet`, main story event infrastructure with per-player flag gates and stat boosts, side event star-rating outcomes, and a new multi-tab Radio Menu (Side Events / Main Quest / Party).

**Status**: 🔴 Not Started

> **Platform**: Forge 1.18.2 (MDK 40.3.0) — Java 17  
> **Design note**: All story state is per-player. There is no global campaign `SavedData` in this phase. `CampaignSavedData` is reserved as a future placeholder only.

---

## Task Tracker

| # | Task | Status | Notes |
|---|------|--------|-------|
| 4.0 | Season architecture design | 🔴 Not Started | Per-player season model; new progression fields added to `CharacterSheet` |
| 4.1 | ~~`CampaignSavedData`~~ (deferred) | ⚪ Deferred | No global state in Phase 4 — all story progression is per-player in `CharacterSheet`. Reserved as a future stub. |
| 4.2 | Main story event infrastructure | 🔴 Not Started | JSON schema extensions; per-player prerequisite checks; per-player `setsPersonalFlags` writes; per-player season advancement |
| 4.3 | Stat upgrade system (milestone-triggered) | 🔴 Not Started | `incrementStat()` in `CharacterSheet`; triggered by `grantsStatBoost` in event JSON |
| 4.4 | Side event outcome tracking (star rating) | 🔴 Not Started | `CompletedSideEventRecord` in `CharacterSheet` NBT; star computed at `endEvent()` |
| 4.5 | Radio Menu Screen (`RadioMenuScreen`) | 🔴 Not Started | 3-tab screen; Shift+Right-click on Radio; Side Events / Main Quest / Party tabs |
| 4.6 | `PartyStatusPacket` (S→C) | 🔴 Not Started | Party snapshot for Party tab; pushed on vote update and disconnect events |
| 4.7 | Stub main story JSON events | 🔴 Not Started | 2 placeholder events: opening episode + season finale |
| 4.8 | End-to-end test | 🔴 Not Started | Flags persist across restart; season transition; star rating; Radio Menu; stat boost |

---

## Architecture

### Season Model

The campaign is divided into seasons, modelled after the show. **Seasons are per-player** — Player A can be in Season 1 while Player B is in Season 3 on the same server. Each season has:
- A set of side events scoped to that season (tagged in JSON with `"season": N`)
- A sequential set of main story events progressing the arc
- A **season finale** main story event that triggers the player's personal transition to the next season

On season transition (per-player):
- `currentSeason` increments in the player's `CharacterSheet`
- The active side event pool switches to season N+1 events for that player
- Season N events move to the **Previous Season** archive in the player's Radio Menu (see Task 4.5)
- Only the immediately previous season's events are archivable; older seasons are read-only history (no replay)

> **Cooldown note**: A cooldown on replaying archived previous-season events is planned but **not implemented in Phase 4**. Leave a clearly marked stub/comment in `RadioMenuScreen` where the cooldown check would go.

### New Per-Player Fields in `CharacterSheet`

All story progression is per-player. The following fields are added to `CharacterSheet` (serialized in capability NBT under the `"character"` subtag):

| Field | Type | Purpose |
|-------|------|---------|
| `currentSeason` | `int` | This player's current season (1-indexed); increments on personal season finale completion |
| `completedMainEvents` | `Set<String>` | IDs of main story events this player has personally completed |
| `witnessedMainEvents` | `Set<String>` | IDs of main story events this player has personally started or participated in; superset of `completedMainEvents`; used by Instance System for progression gating |
| `personalFlags` | `Map<String, Boolean>` | NPC/object outcome flags from events (e.g. `"marcus_alive": false`); written by `endEvent()` via `setsPersonalFlags` in event JSON; read by Instance System |
| `seasonHistory` | `Map<Integer, Set<String>>` | Completed side event IDs per past season (for archive display in Radio Menu) |

### Event JSON Schema Extensions

New fields added to event JSON for Phase 4:

```json
{
  "id": "season_1_episode_1",
  "type": "main",
  "season": 1,
  "order": 1,
  "unlockRequires": [],
  "setsPersonalFlags": { "season_1_started": true },
  "seasonFinale": false,
  "grantsStatBoost": null,
  "scenes": { ... }
}
```

For side events:
```json
{
  "id": "the_barricaded_house",
  "type": "side",
  "season": 1,
  "scenes": {
    "good_outcome": {
      "starRating": 3,
      "terminal": true,
      ...
    },
    "partial_outcome": {
      "starRating": 2,
      "terminal": true,
      ...
    },
    "bad_outcome": {
      "starRating": 1,
      "terminal": true,
      ...
    }
  }
}
```

`starRating` is an optional int (1–3) on terminal scenes. If the event ends on a terminal scene with `starRating`, that rating is recorded. If no `starRating` is on the terminal scene, default to 1★.

### Main Story Progression Rules

1. **No global lock**: Multiple players can independently run the same main story event simultaneously in separate instances. There is no server-wide lock.
2. **Per-player prerequisite check**: `EventQueueManager.startEvent()` checks `CharacterSheetCapability.get(player).completedMainEvents` against the event's `unlockRequires` list. All required event IDs must be present.
3. **Order enforcement**: `order` field is for UI display and human readability only. Prerequisites enforce actual gating.
4. **Personal flag writes**: `endEvent()` merges `setsPersonalFlags` (from the resolved event definition) into the player's `CharacterSheet.personalFlags` via `CharacterSheetCapability.get(player)`, then marks the event ID in `completedMainEvents` and `witnessedMainEvents`. Synced to client via `CharacterSheetSyncPacket`.
5. **Season finale**: If the ending event has `"seasonFinale": true`, `endEvent()` increments `CharacterSheet.currentSeason` for all party participants and archives that season's completed side event IDs into `seasonHistory`.

### Stat Boost on Milestone

`grantsStatBoost` in event JSON (optional, null if absent):
```json
"grantsStatBoost": { "stat": "STRENGTH", "amount": 1 }
```

Applied in `endEvent()` to the instigating player (main story events) or all participants (if designed that way). `CharacterSheet.incrementStat(stat, amount)` enforces the cap of 10. After applying, `CharacterSheetSyncPacket` is sent to the affected client(s).

### Side Event Outcome Tracking

`CompletedSideEventRecord` stored in `CharacterSheet` (capability NBT, per-player, per-world):

```java
public record CompletedSideEventRecord(
    String eventId,
    int season,
    int starRating,      // 1, 2, or 3
    long completedAt     // System.currentTimeMillis() at resolution
) {}
```

Serialized as a `ListTag` of `CompoundTag` entries inside the `CharacterSheet` NBT under key `"completedSideEvents"`. Cap the list at a reasonable size (e.g. 200 entries) to prevent unbounded NBT growth.

Star rating is determined server-side at `endEvent()` by reading the `starRating` field of the terminal `EventScene`. Written once; not recomputed.

### Radio Menu Screen

**Trigger**: Shift + Right-click on Radio item. Works whether or not an event is currently active (`EventScreen` and `RadioMenuScreen` are independent — holding Shift directs the right-click to the menu instead of the event screen).

**Implementation**: `RadioMenuScreen extends Screen` with a 3-tab layout using simple tab buttons at the top. Tab state is client-local (no packet needed to switch tabs).

#### Side Events Tab

- Sections: **Current Season** and **Previous Season** (if `currentSeason > 1`)
- Per event: event display name, star rating (filled/empty stars), or "Not Attempted" in grey if no record exists
- Previous Season section shows events from `currentSeason - 1` only
- > **TODO (Phase 5+)**: Each previous-season event entry should check a cooldown before allowing replay. Insert a `checkReplayEligible(eventId, player)` stub that always returns `true` for now. When the cooldown system is implemented, replace this stub.

#### Main Quest Tab

- Ordered list of main story events (by `"order"` field) for the current season
- Status icons: 🔒 Locked (prerequisites not met) / ⬜ Available / ✅ Complete
- Completed events show their completion timestamp in a subdued colour
- Current season indicator at the top (e.g. "Season 1 — The Fall")
- Past seasons collapsible in a "Story So Far" section (read-only, no interaction)

#### Party Tab

- **If not in a party**: Shows "Not currently in a party." with no interactive elements
- **If in a party**: Populated from the most recent `PartyStatusPacket`
  - Per member row: display name, vote status (Voted ✔ / Pending ⏳ / Offline 💤), ping indicator
  - Abandonment timer: shown as a countdown bar if the timer is running (`timerRemainingSeconds >= 0`)
  - `[Invite Player]` button: opens a text field; on submit, sends the same server-side invite as `/exanira event invite <player>` — validates same guardrails (must be at start scene)
- Party tab does **not** support forming a party outside of an active event. If clicked when not in an event, the `[Invite Player]` button is greyed out with tooltip "You must be in an event to invite players."

### `PartyStatusPacket` (S→C)

Payload:
```java
List<MemberStatus> members   // name, UUID, voteStatus, isOnline
int timerRemainingSeconds    // -1 if no abandonment timer active
String instanceKey           // ignored if empty (not in party)
```

Sent by server in these situations:
- Client opens Radio Menu (client sends a zero-payload `RequestPartyStatusPacket` C→S on tab open)
- Any vote update (piggybacked alongside existing `PartyVoteStatePacket` flow — or sent independently)
- Party member disconnects / reconnects

Cached in `ClientEventState` (or a new `ClientPartyState` if cleaner) and read by `RadioMenuScreen` Party tab on render.

---

## Key Classes (New in Phase 4)

| Class | Role |
|-------|------|
| `character/CompletedSideEventRecord.java` | Record for per-player side event outcome history |
| `client/RadioMenuScreen.java` | 3-tab radio menu; Shift+Right-click entry point |
| `network/PartyStatusPacket.java` | S→C party snapshot for Party tab |
| `network/RequestPartyStatusPacket.java` | C→S zero-payload; triggers server to send `PartyStatusPacket` |

### Modified Classes

| Class | Change |
|-------|--------|
| `event/EventQueueManager.java` | `startEvent()` checks per-player prerequisites from `CharacterSheet.completedMainEvents`; `endEvent()` writes personal flags + stat boost + star rating + season advancement to player's `CharacterSheet` |
| `event/EventDefinition.java` | New fields: `season`, `order`, `unlockRequires` (event IDs player must have completed), `setsPersonalFlags` (`Map<String,Boolean>` written to player's `personalFlags`), `seasonFinale`, `grantsStatBoost` |
| `event/EventScene.java` | New optional field: `starRating` (int, 1–3, present only on terminal scenes) |
| `character/CharacterSheet.java` | Add `List<CompletedSideEventRecord> completedSideEvents`; `incrementStat(stat, amount)`; `Set<String> witnessedMainEvents`; `Set<String> completedMainEvents`; `int currentSeason`; `Map<String, Boolean> personalFlags`; `Map<Integer, Set<String>> seasonHistory` |
| `item/RadioItem.java` | Shift+Right-click → open `RadioMenuScreen`; unshifted Right-click behaviour unchanged |
| `network/ExaniraMod.java` | Register 2 new packets: `PartyStatusPacket`, `RequestPartyStatusPacket` |

---

## JSON Files (New in Phase 4)

```
src/main/resources/data/exanira/events/
    season_1_episode_1.json     — first main story event (stub content)
    season_1_finale.json        — season finale (seasonFinale: true, stub content)
```

Existing side event JSON files updated to add `"season": 1` field. No other changes to existing event logic.

---

## Known Design Notes & Deferred Items

| Topic | Note |
|-------|------|
| Per-player flag architecture | All story state is per-player. `CharacterSheet.witnessedMainEvents` + `completedMainEvents` = personal story progression. `CharacterSheet.personalFlags` = per-player NPC/choice outcomes. `CharacterSheet.currentSeason` = which season this player is on. All fields are read by the Instance System (Phase 6) when generating a player's view of a location. |
| No global state in Phase 4 | `CampaignSavedData` is not implemented. Reserved as a future placeholder only. |
| Previous-season replay cooldown | Stub `checkReplayEligible()` in `RadioMenuScreen` returns `true` always. Cooldown implementation deferred to Phase 5+. |
| Season naming | `"Season 1 — The Fall"` etc. is placeholder. Season display names should be defined in either `en_us.json` or a `seasons.json` data file. Leave a TODO comment. |
| Stat cap enforcement | `incrementStat` silently clamps at 10. No message to player. Consider adding a "Stat maxed" notification in a later pass. |
| Location instances are state-matched | Instances are determined by each player's personal progression and choices. Players with matching state (same chapter, same relevant decisions) can share an instance. Players with differing state each enter the instance matching their own experience. Whether state-matched players share a single server-side space or get separate identical spaces is an open Phase 6 design decision. |

---

## Phase 5 Preview — Horde Director

- Design decision required before Phase 5 begins: custom entity subclass vs. Forge Capability on vanilla mobs for the limb system
- Spawn pressure director (noise → horde weight)
- Horde state machine: `Dormant → Roaming → Tracking → Attacking → Dispersing`
