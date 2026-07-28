# Phase 4 Execution — Main Story System

**Goal**: Per-player story progression and season model stored in `CharacterSheet`, main story event infrastructure with per-player flag gates and stat boosts, side event star-rating outcomes, and a new multi-tab Radio Menu (Side Events / Main Quest / Party).

**Status**: 🔄 Active — core feature work complete; party tab polish (Task 4.9) in progress

> **Platform**: Forge 1.18.2 (MDK 40.3.0) — Java 17  
> **Design note**: All story state is per-player. There is no global campaign `SavedData` in this phase. `CampaignSavedData` is reserved as a future placeholder only.

---

## Task Tracker

| # | Task | Status | Notes |
|---|------|--------|-------|
| 4.0 | Season architecture design | 🟢 Complete | Per-player season model; new progression fields added to `CharacterSheet` |
| 4.1 | ~~`CampaignSavedData`~~ (deferred) | ⚪ Deferred | No global state in Phase 4 — all story progression is per-player in `CharacterSheet`. Reserved as a future stub. |
| 4.2 | Main story event infrastructure | 🟢 Complete | JSON schema extensions; per-player prerequisite checks; per-player `setsPersonalFlags` writes; per-player season advancement |
| 4.3 | Stat upgrade system (milestone-triggered) | 🟢 Complete | `incrementStat()` in `CharacterSheet`; triggered by `grantsStatBoost` in event JSON |
| 4.4 | Side event outcome tracking (star rating) | 🟢 Complete | `CompletedSideEventRecord` in `CharacterSheet` NBT; star computed at `endEvent()`; star ratings added to all terminal scenes in `the_barricated_house.json` |
| 4.5 | Radio Menu Screen (`RadioMenuScreen`) | 🟢 Complete | 3-tab screen; Shift+Right-click on Radio; Side Events / Main Quest / Party tabs; GUI-scale-aware panel dimensions |
| 4.6 | `PartyStatusPacket` (S→C) | 🟢 Complete | Party snapshot for Party tab; pushed on `RequestPartyStatusPacket`; added `voteInProgress` field |
| 4.7 | Stub main story JSON events | 🟢 Complete | 2 placeholder events: opening episode + season finale |
| 4.8 | End-to-end test + bug-fix pass | 🟢 Complete | See Bug-Fix Pass sections below |
| 4.9 | Party tab polish | 🔴 In Progress | Live refresh, player heads, vote status fix, layout reorder — see Task 4.9 section |

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
| `client/RadioMenuScreen.java` | 3-tab radio menu; Shift+Right-click entry point; GUI-scale-aware; party invite UI |
| `network/PartyStatusPacket.java` | S→C party snapshot for Party tab; includes `voteInProgress` flag |
| `network/RequestPartyStatusPacket.java` | C→S zero-payload; triggers server to send `PartyStatusPacket` |
| `network/SendInvitePacket.java` | C→S; sends party invite by name from Radio Party tab |
| `network/InviteNotificationPacket.java` | S→C; delivers invite notification to invitee's `ClientEventState` |
| `network/AcceptInvitePacket.java` | C→S; accepts a specific invite by `instanceKey` |

### Modified Classes

| Class | Change |
|-------|--------|
| `event/EventQueueManager.java` | `startEvent()` returns `StartResult` enum; blocks re-running completed MAIN events; `joinEvent()` returns `JoinResult` enum and checks prerequisites for MAIN events; `processInvite()` checks invitee prerequisites before sending invite and centralizes invite logic; `clear()`/`shutdownAll()` reset `savedEventData = null` to prevent cross-world ghost events |
| `event/EventDefinition.java` | New fields: `season`, `order`, `unlockRequires`, `setsPersonalFlags`, `seasonFinale`, `grantsStatBoost` |
| `event/EventScene.java` | New optional field: `starRating` (int, 1–3, present only on terminal scenes) |
| `character/CharacterSheet.java` | Added `completedSideEvents`, `incrementStat()`, `witnessedMainEvents`, `completedMainEvents`, `currentSeason`, `personalFlags`, `seasonHistory` |
| `client/ClientEventState.java` | Added `PendingInvite` record, `pendingInvites` list, `addPendingInvite()`, `removePendingInvite()`, `getPendingInvites()`; `clear()` now also clears pending invites |
| `item/RadioItem.java` | Shift+Right-click → open `RadioMenuScreen`; unshifted Right-click behaviour unchanged |
| `ExaniraMod.java` | Registers 13 total packets (3 new in bug-fix pass: `SendInvitePacket`, `InviteNotificationPacket`, `AcceptInvitePacket`) |
| `command/ExaniraCommands.java` | `executeEventStart()` uses `StartResult`; `executeEventInvite()` delegates to `EventQueueManager.processInvite()` |

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


---

## Bug-Fix Pass (post-4.8 testing)

The following bugs were discovered during end-to-end testing and fixed before Phase 4 was closed.

### Critical Bugs Fixed

| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Season finale could be repeated, advancing season multiple times | `startEvent()` only checked prerequisites; no check for already-completed events | `startEvent()` now returns `StartResult` enum; added `completedMainEvents.contains(eventId)` guard for MAIN events |
| Active event state persisted across world deletion (cross-world ghost events) | `savedEventData` (the `ActiveEventSavedData` reference) was never reset between world sessions, so `restoreEventsFromSave()` on a new world loaded the old world's data | `clear()` and `shutdownAll()` now set `savedEventData = null`; `restoreEventsFromSave()` fetches a fresh handle from the current server |
| Party invite bypassed main story prerequisites | `joinEvent()` had no prerequisite check; invited players could skip story gates | `joinEvent()` now runs the same MAIN event prerequisite check as `startEvent()` |
| Duplicate error message when prerequisites failed | `startEvent()` sent an in-world message AND the command handler printed a second generic failure message | `startEvent()` returns `StartResult` (`SUCCESS` / `ALREADY_IN_EVENT` / `PREREQ_FAILED` / `ALREADY_COMPLETED`); command only shows generic message for `ALREADY_IN_EVENT` |

### High-Priority Fixes

| Item | Fix |
|------|-----|
| Star ratings always showed 1★ regardless of outcome | Terminal scenes in `the_barricated_house.json` were missing `starRating` fields; added: `earned_trust`=3, `false_trust`=2, walk/threaten/reject=1 |
| `EditBox` suggestion text pushed alongside typed characters | `inviteBox` promoted to class field; `tick()` override clears suggestion when value is non-empty |
| Party tab "Pending" label was confusing | Added `voteInProgress` boolean to `PartyStatusPacket`; Party tab now shows `[In Party]` when no vote is active, `[Waiting]` / `[Voted]` during an active choice vote |
| Party invites required chat commands | Added three new packets: `SendInvitePacket` (C→S), `InviteNotificationPacket` (S→C), `AcceptInvitePacket` (C→S). Party tab now shows incoming invites with clickable Accept buttons; no commands needed |
| Screen layout broken at non-default GUI scales | Panel dimensions (`panelW`, `panelH`) are now computed in `init()` and clamped to 94%/92% of the viewport, replacing the previous hardcoded 380×260 constants |
| Screen spacing looked wrong after season finale | "No main story events loaded." replaced with softer message; "Story So Far" entries given distinct colour; `contentMaxY` calculation corrected to use dynamic `panelH` |

### New Packets (Phase 4 Bug-Fix Pass)

| Packet | Direction | Purpose |
|--------|-----------|---------|
| `SendInvitePacket` | C→S | Sends an invite by player name from the Radio Party tab; replaces the old `/exanira event invite` chat-command route |
| `InviteNotificationPacket` | S→C | Delivers an invite notification to the invitee's `ClientEventState`; displayed in the Party tab with Accept button |
| `AcceptInvitePacket` | C→S | Accepts a specific pending invite by `instanceKey`; calls `joinEvent()` server-side |

Total registered packets after this pass: **13**.

---

## Bug-Fix Pass 2 (post-4.8 second round of testing)

The following bugs were discovered during a second round of testing and fixed after the first bug-fix pass.

### Bugs Fixed

| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Double error message when invite accept fails due to prerequisites | `joinEvent()` sent the prereq message AND `AcceptInvitePacket` always appended a second generic "Could not join" message | `joinEvent()` now returns a `JoinResult` enum (`SUCCESS` / `NOT_FOUND` / `NOT_AT_START` / `ALREADY_IN_EVENT` / `PREREQ_FAILED`); `AcceptInvitePacket` suppresses the generic message when the result is `PREREQ_FAILED` |
| Party invite sent to a player who hadn't completed prerequisites | `processInvite()` only checked the **inviter's** event state; the invitee's completed events were never checked | `processInvite()` now checks the invitee's `completedMainEvents` against the event's `unlockRequires` before calling `setPendingInvitation()`; the inviter receives a clear explanation if the invitee isn't ready |
| Main Quest tab text overlapping | `mainEvents.isEmpty()` branch drew the "No new stories this season yet." line but never advanced `y`, so the "Story So Far" section rendered on top of it | Added the missing `y += LINE_H` after the empty-events `drawString` call |
| Accept button in Party tab had no visible border | Button was rendered with a single `fill()` (dark green background only) | Now drawn as two stacked fills: a 1 px `0xFF336633` border fill, then the `0xFF1A441A` background on top; click-detection area expanded to match the border |
| Accept button sat lower than the invite text beside it | Button box spanned `y-1` to `y+LINE_H+1`; visual center was 2 px below the text center (`y+4.5`) | Box tightened to `y-2` to `y+11` (background) and `y-3` to `y+12` (border), giving a center at `y+4.5` to match Minecraft's 9 px font center |

---

## Task 4.9 — Party Tab Polish

**Status**: 🔴 In Progress

Issues found during testing that need to be addressed before the party tab is considered production-ready.

### Items

| # | Item | Priority | Notes |
|---|------|----------|-------|
| 4.9-A | Party tab live refresh | High | Tab only reflects state at the moment `RequestPartyStatusPacket` was last sent (on open). When a player joins the party or leaves, the client doesn't see the change until they close and reopen the screen. The disconnect timer also doesn't count down live. Fix: add a `tick()` rate-limited re-request (e.g. every 40 ticks / 2 s) and push `PartyStatusPacket` server-side on any party membership change. |
| 4.9-B | Player head icon next to party member names | Medium | Each party member row should show a 8×8 or 16×16 head texture to the left of the display name. Use `AbstractClientPlayer`'s skin texture if online; fall back to the default Steve/Alex skin for offline members. Requires looking up `Minecraft.getInstance().getConnection().getOnlinePlayerById(uuid)` to get the `PlayerInfo` and skin texture resource location. |
| 4.9-C | Vote status not shown after first scene | Medium | After the first scene, all members show `[In Party]` instead of `[Waiting]`/`[Voted]`. This may be because the test scenes had no choices (single-option scenes), which means `voteInProgress` is always `false` and the tab correctly shows `[In Party]`. **Needs verification**: run through a scene that has multiple choices and confirm `[Waiting]`/`[Voted]` labels appear during the vote. If they don't, the bug is in `buildPartyStatus()` computing `voteInProgress` incorrectly. |
| 4.9-D | Layout: "Not currently in a party" above "No pending invitations" | Low | Current render order: pending invites section first, then party section (which shows "Not currently in a party"). When there are no invites AND no party, the empty-invite message renders above the no-party message, which reads awkwardly. Fix: show the party status ("Not currently in a party.") first, then the pending invites section below it. || 4.9-E | Block invites to already-completed main story events | Medium | `processInvite()` currently only checks that the invitee meets prerequisites. It does not check whether the invitee has already completed the same event. If the event is MAIN and the invitee's `completedMainEvents` already contains the event ID, the invite should be rejected with a message to the inviter (e.g. "Player X has already completed that event."). This mirrors the guard in `startEvent()` that blocks solo replays. |
### Implementation Notes

- **4.9-A (live refresh)**: Server-side pushes are the cleanest approach — add a call to `broadcastPartyStatus(active)` inside `joinEvent()` after a participant is successfully added, so all existing party members get an updated snapshot automatically. Client-side polling (tick-based re-request) covers the disconnect timer countdown.
- **4.9-B (heads)**: Minecraft renders player heads in inventory GUIs using `InventoryScreen.renderEntityInInventory` or by directly binding the skin texture and drawing a 8×8 UV region from the face layer of the 64×64 skin atlas. The simpler path is to draw the face quad manually: UV (8,8)→(16,16) on a 64×64 texture = `u0=8/64, v0=8/64, u1=16/64, v1=16/64`. Fall back to `DefaultPlayerSkin.getDefaultSkin(uuid)` for offline/unknown players.
- **4.9-C (vote status)**: Check `EventQueueManager.buildPartyStatus()` — `voteInProgress` is set to `!active.currentScene().choices().isEmpty()`. If a scene genuinely has no choices (single `[Continue]` option), this is correct behaviour. Test with `the_barricated_house.json` which has multi-choice scenes.
- **4.9-D (layout reorder)**: In `renderPartyTab()`, move the "Not currently in a party" / "Start an event" block to render before the pending invites loop, then render invites below.

## Phase 5 Preview — Horde Director

- Design decision required before Phase 5 begins: custom entity subclass vs. Forge Capability on vanilla mobs for the limb system
- Spawn pressure director (noise → horde weight)
- Horde state machine: `Dormant → Roaming → Tracking → Attacking → Dispersing`
