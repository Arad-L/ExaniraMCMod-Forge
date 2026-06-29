# Phase 3 Execution — Multiplayer Logic

**Goal**: Multiple players share the same event instance via invite, decisions resolve as a group vote with majority rule, the event lock system is enforced, and the `successEvent` cross-event chain works fully.

**Status**: 🟡 In Progress — party formation, server-side voting, vote state UI, and persistence are fully implemented; logout handling remains.

> ⚠️ **Downport Note**: Originally developed on NeoForge 1.21.1.  
> Downported to Forge 1.18.2 (MDK 40.3.0). Key changes in this phase:  
> - `CharacterAttachment.CHARACTER_SHEET.get()` / `player.getData(...)` → `CharacterSheetCapability.get(player).orElseGet(CharacterSheet::new)`  
> - `CharacterAttachment.PENDING_EVENT.get()` / `player.getData(...)` → `PendingEventCapability.get(player).ifPresent(...)`  
> - `PacketDistributor.sendToPlayer(player, packet)` → `CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet)`  
> - `ServerLifecycleHooks.getCurrentServer()` — same package, `net.minecraftforge.server.ServerLifecycleHooks`

---

## Task Tracker

| # | Task | Status | Notes |
|---|------|--------|-------|
| 3.0b | Debug stop command | 🟢 Complete | `/exanira event stop` (self) and `/exanira event stop <player>` — op level 2 |
| 3.1 | Event locking | 🟢 Complete | `playerToEvent` map blocks double-join; feedback message on attempt |
| 3.2 | `successEvent` cross-event chain | 🟢 Complete | Terminal choices can define `successEvent`; `endEvent()` chains to next event for the instigator |
| 3.3 | Multi-scene event structure | 🟢 Complete | Events are `Map<sceneId, EventScene>`; `nextScene` field navigates within event; terminal scenes auto-dismiss |
| 3.4 | Offline auto-resolve + persistence | 🟢 Complete | `PendingEventAttachment` (capability) stores `instanceKey` + `eventId` + `sceneId`; `resyncPlayerIfMidEvent()` reconstructs in-memory state on login; per-world isolation automatic via capability NBT in `playerdata/` |
| 3.5 | Party formation via invite | 🟢 Complete | `/exanira event invite <player>` and `/exanira event accept`; invites sent only from start scene; join allowed only at start scene; duplicate invite to same instance blocked |
| 3.6 | Server-side party vote | 🟢 Complete | `ActiveEvent` collects votes per scene; `allVoted()` + `resolveMajorityChoice()` triggers `applyChoice()`; stat gate checked before vote recorded |
| 3.7 | Vote state UI (EventScreen) | 🟢 Complete | `PartyVoteStatePacket` registered and sent on all vote updates; vote counts shown right-aligned on choice buttons; selected vote highlight persists from server-authoritative `localChoiceIndex` |
| 3.8 | Logout mid-party handling | 🔴 Not started | When a party member disconnects, auto-submit their vote using the event's `offlineFallback` strategy IF the other party members have ALL voted starting a five minute timer and it passed; remove from participants so `allVoted()` recalculates. Huge issue: Currently, if the server shuts down during the event it does not save the votes and the party itself is also dissipated |
| 3.9 | End-to-end multiplayer test | 🔴 Not started | Two `runClient` instances; same event instance; vote on a choice; verify both clients advance to the same scene; test mid-event disconnect |

---

## Bug Fixes Applied (2026-06)

The party system was initially stubbed in by an external AI without full codebase context, introducing 6 critical bugs. All are now fixed.

| ID | Symptom | Root Cause | Fix Applied |
|----|---------|-----------|-------------|
| **A** | Radio stayed glowing after event end; `/event stop` said "not in event" | `endEvent` took a `ServerPlayer` param that callers always passed as `null`, skipping all cleanup | Removed `player` param; `endEvent` now looks up every participant from `ServerLifecycleHooks.getCurrentServer()` and sends `EventEndPacket` to all |
| **B** | Reconnect after server restart sent nothing (blank screen) | `resyncPlayerIfMidEvent` only checked in-memory maps, which are empty after restart | Restored full capability-read path: reads `PendingEventAttachment`, reconstructs `ActiveEvent`, re-sends scene |
| **C** | `loadedEvents.get(instanceKey)` always returned null after restart | `startEvent` called 2-arg `pending.set(instanceKey, sceneId)`, stuffing the full instanceKey into the `eventId` field | Extended `PendingEventAttachment` to 3 fields; all callers use 3-arg `set(instanceKey, eventId, sceneId)` |
| **D** | Scene advances not persisted — reconnect always returned player to first scene | `broadcastScene` never updated the attachment on scene change | Added `pending.set(...)` in `broadcastScene` for every online participant after every scene advance |
| **E** | Party joiner lost persistence — radio cleared on reconnect | `joinEvent` never set the attachment for the new joiner | Added `pending.set(...)` in `joinEvent` |
| **F** | Stat gate checks silently removed — any player could take any choice | `meetsRequirements()` call was dropped during refactor | Restored in `resolveChoice`: checked before recording party vote and before applying solo choice |

---

## Architecture

### Instance Key Format

```
instanceKey = eventId + "_" + UUID.randomUUID()
```

Unique per event run. Multiple parties can run the same event definition simultaneously with full isolation.

### Vote Flow (Party)

```
Each player → EventChoicePacket → resolveChoice()
    → stat gate check (meetsRequirements via CharacterSheetCapability.get(player))
    → active.recordVote(playerId, choiceIndex)
    → broadcast PartyVoteStatePacket to all participants
    → if active.allVoted():
        resolveMajorityChoice() → applyChoice()
            → broadcastScene()   [updates PendingEventAttachment for each participant]
            OR endEvent()        [clears all participants, radio off, EventEndPacket]
```

### Persistence Fields (`PendingEventAttachment` — Forge Capability)

| Field | NBT Key | Purpose |
|-------|---------|---------|
| `instanceKey` | `"instanceKey"` | Full runtime key used for `activeEvents` map lookup |
| `eventId` | `"eventId"` | Event definition ID used for `loadedEvents.get()` on reconnect |
| `sceneId` | `"sceneId"` | Current scene so reconnect resumes where the player left off |

Stored via `ExaniraCapabilityProvider` (implements `ICapabilitySerializable`) in the player's own NBT save file (`playerdata/UUID.dat`). Per-world isolation is automatic — each world has its own `playerdata/` folder.

### Command Tree

```
/exanira event start <id>       — start a new event instance (op level 2)
/exanira event invite <player>  — invite player to your current event (start scene only)
/exanira event accept           — accept a pending invitation
/exanira event stop [<player>]  — force stop self or target (op level 2)
```

### Key Classes

| Class | Role |
|-------|------|
| `event/ActiveEvent.java` | Runtime state: participants set, current scene id, vote map, majority resolution |
| `event/EventQueueManager.java` | Central authority: start/join/end lifecycle, invite map, all scene transitions, reconnect resync |
| `event/PendingEventAttachment.java` | Player capability: 3-field NBT persistence for cross-restart reconnect |
| `event/PendingEventCapability.java` | `Capability<PendingEventAttachment>` declaration via `CapabilityToken` |
| `character/CharacterSheetCapability.java` | `Capability<CharacterSheet>` declaration via `CapabilityToken` |
| `character/ExaniraCapabilityProvider.java` | `ICapabilitySerializable` hosting both capabilities; attached via `PlayerCapabilityHandler` |
| `handlers/PlayerCapabilityHandler.java` | `AttachCapabilitiesEvent` + `PlayerEvent.Clone` (copies caps on respawn/dim change) |
| `command/ExaniraCommands.java` | All 4 subcommands with invite guardrails (start-scene-only, duplicate-invite blocking) |
| `network/PartyVoteStatePacket.java` | S→C: vote tally + recipient-local selected choice index for persistent highlight |
| `client/EventChoiceButton.java` | Button with vote count badge, selection highlight, and `lockedTooltip` field |
| `client/EventScreen.java` | Full event screen: scrollable dialogue, choice buttons, tooltip rendering, `refresh()` + `updateVoteCounts()` |

---

## What Remains for Phase 3 Completion

### 1. Logout mid-party handling (Task 3.8)
- Hook into `PlayerEvent.PlayerLoggedOutEvent` (server-side, Forge event)
- If the logging-out player is in a party event:
  - Record an auto-vote using the event's `offlineFallback` choice (or abstain with index `-1`)
  - Remove from `active.participants()` so `allVoted()` recalculates correctly
  - If only one participant remains, downgrade to solo mode (no more vote UI)
- Remove the player's pending invitation if any

### 2. End-to-end multiplayer test (Task 3.9)
- Launch `runClient` and `runClient2`
- Player 1: `/exanira event start abandoned_radio_station`
- Player 1: `/exanira event invite Player2`
- Player 2: `/exanira event accept`
- Both vote on a choice; verify both clients advance to the same next scene
- Player 1 leaves mid-event; verify Player 2's remaining vote still resolves

---

## Known Design Weaknesses (Deferred)

| Weakness | Risk | Deferral Rationale |
|----------|------|--------------------|
| No atomic transition guard | Rapid concurrent votes could double-execute `applyChoice` | Low risk for current player counts; add `synchronized` block or flag in Phase 4 |
| Tie-break is deterministic (first max index wins) | Same tie always resolves the same way | Acceptable; randomise in a later pass |
| Join window relies on scene comparison, no explicit flag | Edge case: invite accepted exactly as scene advances | Add `joinLocked` boolean to `ActiveEvent` when this becomes a real problem |
| Invitations can become stale after scene progression | Joiner arrives at wrong scene | Mitigated by join-window enforcement; low frequency |
| No formal lifecycle FSM | Hard to reason about `START → ACTIVE → RESOLVED → ENDED` transitions | Not blocking for Phase 3 |

---

## Phase 4 Preview — Main Story System

- Global story flags (`SavedData` — world-persistent, survives server restart)
- Main story events are manually initiated.
- Players are invited or join voluntarily.
- Standard event lock rules apply.
- **Forge 1.18.2 note**: `SavedData` API is available and stable in 1.18.2; no migration needed for Phase 4.
