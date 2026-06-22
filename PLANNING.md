# Narrative Event Engine — Final System Architecture

> **Narrative Event Engine + RPG Character Layer + Horde Director** on top of Minecraft  
> **Platform: Forge 1.18.2 (MDK 40.3.0) — Java 17**

> ⚠️ **Downport Note (2026-06-21)**: The mod was originally developed and verified on NeoForge 1.21.1.
> It has been fully downported to Forge 1.18.2-40.3.0 with zero functionality loss.
> All NeoForge-specific APIs have been replaced with their Forge 1.18.2 equivalents.
> See individual Phase documents for per-file migration notes.

Three core subsystems:

---

# 1. Narrative Event Engine (THE CORE)

## State Model

- **Global Campaign State (Server-wide)** — main story progression, world flags (cities fallen, NPC alive/dead, etc.)
- **Party State** — everyone participating in the same active event forms a **temporary party** for the duration of that event only. No persistent party system.
- **Player State** — character sheet, stats, personal flags/backstory consequences

---

## Event Types

### Main Story Events (GLOBAL LOCKED)
- Only one active at a time
- All online players are **forced into the event automatically** — no opt-in prompt
- A **5-minute accept window** is shown. If not all players accept within 5 minutes (or all decline), the event is dismissed and rescheduled to attempt again later
- Blocks other main story progression while active

### Side Events (PARALLEL)
- Per-player or per-temporary-party
- Up to 3 simultaneous server-wide
- Players can only be in ONE side event at a time (event lock)

### Ambient Events
- Passive world narration
- No player lock
- Horde pressure / radio chatter / environmental storytelling

---

## Event Engine Loop

```
tick →
  check triggers →
    spawn event →
      assign players (or force-join for main story) →
        wait for input →
          resolve outcome (hard gate skill checks) →
            update state →
              clean up event
```

### Core Design Principles

- **Instance isolation** — every event run gets a unique `instanceKey = eventId + "_" + UUID`. Multiple parties can run the same event definition simultaneously with no shared state.
- **Server authority** — the server is the single source of truth for event state, scene progression, and vote resolution. Clients only submit choices and receive state updates.
- **Party synchronisation** — all participants in an instance share the same scene and receive identical dialogue and choices. Transitions are simultaneous.
- **Controlled join window** — players may only join an event instance at its start scene. Once the scene has advanced, the join window is closed.
- **Vote-based party decisions** — when the instance has more than one participant, all votes are collected before `applyChoice()` runs. Majority wins.

### Critical: EventQueueManager

> **WARNING**: The event system MUST be routed through a central `EventQueueManager`. Never attach event logic directly to individual player tick handlers. Per-player scattered logic causes multiplayer desync and duplicate event spawns.

The `EventQueueManager` owns:
- The active event list (`Map<String, ActiveEvent>` keyed by `instanceKey`)
- Player-to-event assignment map (`Map<UUID, String>`)
- Pending invitation map (`Map<UUID, String>` — stores invitee UUID → instanceKey)
- The event scheduling queue
- The 5-minute main story accept timer

### Phase 4 Edge Case (Unresolved)
> If a player already holds a side event lock when a main story event fires, behavior is not yet defined. Options: side event auto-resolves, pauses, or main story waits until lock clears. Must be decided before Phase 4 implementation.

---

## Offline Player Handling

If a player disconnects mid-event, their pending choices **auto-resolve** using a defined fallback strategy per event (e.g. `"ignore"`, `"flee"`, or a configurable default in the event JSON). The event continues for remaining players without pausing.

---

# 2. Character System (RPG LAYER)

## Character Sheet

### Core Stats
**Stat range: 1–10.** Values set at character creation. Upgradeable in-game up to the cap of 10.

| Stat | Description |
|---|---|
| Strength | Physical power, melee effectiveness |
| Agility | Speed, evasion, quick movement |
| Intelligence | Tech, problem-solving, crafting |
| Perception | Awareness, detection, loot quality |
| Leadership | Party bonuses, NPC relations |
| Survival | Endurance, foraging, wilderness |

### Derived Values (computed from stats)
- Stealth effectiveness — `AGILITY × 2`
- Loot quality bonus — `PERCEPTION + INTELLIGENCE`
- Horde detection range modifier — `PERCEPTION × 3` (blocks)
- Leadership derived effects — deferred to Phase 3

---

## Character Creation Flow

On **first login**, a blocking multi-step screen interrupts the player before they can move. The screen cannot be closed with Escape.

### Step 1: Profession Selection
Player chooses from 8 professions. Each profession sets a fixed stat preset (total: 20 points distributed across 6 stats):

| Profession | STR | AGI | INT | PER | LEAD | SUR |
|---|---|---|---|---|---|---|
| Soldier | 5 | 3 | 2 | 3 | 4 | 3 |
| Nurse | 2 | 3 | 5 | 4 | 3 | 3 |
| Mechanic | 4 | 3 | 4 | 2 | 2 | 5 |
| Teacher | 2 | 2 | 5 | 3 | 5 | 3 |
| Scavenger | 3 | 5 | 3 | 4 | 2 | 3 |
| Farmer | 4 | 2 | 2 | 3 | 2 | 7 |
| Radio Operator | 2 | 2 | 4 | 5 | 3 | 4 |
| Firefighter | 4 | 4 | 2 | 3 | 4 | 3 |

### Steps 2–6: Lifestyle Questions (5 questions)
Each question has 3 options. Each option adds **+1 to one specific stat** on top of the profession preset.

Questions (confirmed):
1. "How did you stay fit?" → STR / AGI / SUR
2. "What sharpened your mind?" → INT / PER / LEAD
3. "What did you do in your spare time?" → INT / SUR / STR
4. "When conflict arose, you..." → LEAD / PER / AGI
5. "What would others say about you?" → STR / PER / SUR

### Step 7: Confirmation
Player sees a confirmation message. Pressing **"Begin Your Story"** locks the choices and submits them to the server.

**No stat preview is shown during selection** — the final sheet is revealed after confirmation.

---

## Backstory Generation

Template engine using the player's actual selections. Key identity details (profession, physical background, hobby) are deterministic from choices. Minor flavor details (location, loss, distrust) are drawn randomly from pools on each character creation.

Template structure:
```
"You were a {profession} in {location}. You {physical_background}.
When you weren't working, you {hobby}.
When everything fell apart, you lost {loss}. Now you trust {distrust} the least."
```

Output stored in `CharacterSheet.backstory` (NBT via Forge Capability provider).

---

## Skill Checks — Hard Gates

Skill checks are **deterministic hard gates only**. No dice, no randomness:

```
success = (player.getStat(skill) >= requirement)
```

- If the player meets the threshold → option is available and succeeds
- If not → option is locked/greyed out in the UI
- Narrative tension comes from choices and consequences, not RNG

`checkType` field is present in the event schema to allow future expansion without breaking changes. Always `"hard"` for now.

---

# 3. Event System (HYBRID SCRIPTING)

## JSON defines:
- Event structure, type, dialogue, choices, triggers, stat requirements, offline fallback action

## Java handles:
- World effects, NPC spawning/despawning, loot generation, horde triggers, complex branching logic

## Example Event Schema

```json
{
  "id": "abandoned_radio_station",
  "type": "side",
  "npc": "HologramSurvivor",
  "startScene": "intro",
  "offlineFallback": "ignore",
  "scenes": {
    "intro": {
      "dialogue": ["You hear static...", "A voice breaks through the radio..."],
      "choices": [
        {
          "text": "Respond to the signal",
          "requires": { "perception": 3 },
          "checkType": "hard",
          "successEvent": "safe_contact",
          "lockedText": "You lack the perception to read this situation."
        },
        { "text": "Ignore it", "outcome": "nothing_happens" }
      ]
    }
  }
}
```

---

# 4. Horde System (DIRECTOR LAYER)

> Hordes are a **dynamic world pressure mechanic**, not individual mob AI.

## Spawn Director

Increases spawn weight based on:
- Noise generated by players
- Time since last horde
- Player density in an area

## Horde States

`Dormant → Roaming → Tracking → Attacking → Dispersing`

## Limb System

> ⚠️ **OPEN DESIGN POINT — Implementation Not Planned**
>
> Whether zombie entities will be subclassed (custom entity types) or have data attached to vanilla entities via Forge Capabilities is **not yet decided**. This affects whether speed/attack modifications are done via AI goal overrides or event hooks. This section must be revisited before Phase 5. Keep limb system implementation entirely separate from horde pressure logic so the two can be built independently.

---

# 5. NPC System

Event NPCs will use a **pre-existing NPC mod** compatible with Forge 1.18.2 that renders player-model entities. The event system will call into that mod's API to spawn and despawn NPCs at event start/end. Dialogue triggering and interaction hooks will be implemented via Forge events rather than reimplemented from scratch.

> **TODO before Phase 6**: Evaluate NPC mod options. Requirements: Forge 1.18.2 compatibility, programmatic spawn/despawn API, player-skin-style rendering.

---

# 6. Event UI System

## Chat Layer (lightweight)
- Quick alerts, narration text, short prompts

## GUI Layer (main interaction)
- Choice buttons (locked/available state based on hard gate checks)
- Stat requirement displayed next to locked choices (`[PER 3+]` prefix shown on all choices)
- Hover tooltip on locked buttons showing `lockedText` (implemented via `Screen.renderTooltip()`)
- Party vote UI — vote counts shown right-aligned on each choice button; selected choice highlighted in blue
- Main story 5-minute accept countdown display (Phase 4)

## Event Locking
- Player receives an **event lock** on joining any side event
- Lock prevents joining other side events
- Lock is released on event resolution (success, failure, or player logout/auto-resolve)

---

# 7. Data Architecture (Forge 1.18.2)

| Concern | Forge 1.18.2 API | Notes |
|---|---|---|
| Server-wide campaign state | `SavedData` | World-persistent story flags (Phase 4+) |
| Character sheet (player data) | `Capability<CharacterSheet>` via `ExaniraCapabilityProvider` | Declared with `CapabilityManager.get(new CapabilityToken<CharacterSheet>(){})` in `CharacterSheetCapability`; provider attached via `AttachCapabilitiesEvent<Entity>` in `PlayerCapabilityHandler` |
| Pending event state (player data) | `Capability<PendingEventAttachment>` via `ExaniraCapabilityProvider` | Declared with `CapabilityManager.get(new CapabilityToken<PendingEventAttachment>(){})` in `PendingEventCapability`; same provider as above |
| Active event state (runtime) | `EventQueueManager` (in-memory) | Lost on server restart; reconstructed on player login via `resyncPlayerIfMidEvent()` |
| Active event state (persistence) | `PendingEventAttachment` (capability NBT) | Stored in player NBT (`playerdata/UUID.dat`) via `ICapabilitySerializable`; 3 fields: `instanceKey`, `eventId`, `sceneId`; per-world automatically |
| UI / networking | `SimpleChannel` (`NetworkRegistry.newSimpleChannel`) | 8 packets: 3 C→S, 5 S→C; S→C packets registered with `Optional.of(NetworkDirection.PLAY_TO_CLIENT)` |

> **Note**: The old `@CapabilityInject` pattern was removed in Forge 40.x. Use `CapabilityManager.get(new CapabilityToken<T>(){})` for static capability field declaration. No explicit `register()` call is needed when the provider implements `ICapabilitySerializable`.
>
> **Note**: `SavedData` (`ExaniraEventSavedData`) was previously tried for active event persistence but dropped due to Windows `AccessDeniedException` during atomic temp-file renames. `PendingEventAttachment` (player NBT via capability) is the correct approach.

---

# MVP Build Order

## Phase 1 — Foundation
- [🟢 Complete] Player character sheet (Forge Capability + NBT serialization)
- [🟢 Complete] Core stat system
- [🟢 Complete] MADLIBS backstory generator
- [🟢 Complete] Character creation screen (profession → 5 lifestyle questions → confirm)
- [🟢 Complete] Character sheet display GUI ([C] keybind)

## Phase 2 — Event Engine (minimum)
- [🟢 Complete] JSON event loader (`data/exanira/events/*.json`, reloads on `/reload`)
- [🟢 Complete] `EventQueueManager` singleton
- [🟢 Complete] Event trigger (command-only: `/exanira event start <id>`)
- [🟢 Complete] Choice UI (hard gate locks, stat badge on all choices, hover tooltip for locked)
- [🟢 Complete] Event state tracking + offline reconnect resync
- [🟢 Complete] Radio item (enchantment foil glow when event active; right-click opens EventScreen)

## Phase 3 — Multiplayer Logic
- [🟢 Complete] Event locking system (`playerToEvent` map)
- [🟢 Complete] Debug stop command (`/exanira event stop [<player>]`)
- [🟢 Complete] `successEvent` cross-event chaining
- [🟢 Complete] Multi-scene event structure (`nextScene` field)
- [🟢 Complete] Offline auto-resolve + persistence (`PendingEventAttachment`; `resyncPlayerIfMidEvent`)
- [🟢 Complete] Party formation via invite (`/exanira event invite` + `accept`)
- [🟢 Complete] Server-side party vote (majority resolution; stat gate enforced before vote recorded)
- [🟢 Complete] Vote state UI (`PartyVoteStatePacket`; vote counts on buttons; selection highlight)
- [ ] Logout mid-party handling (auto-vote on disconnect; remove from participants)

## Phase 4 — Main Story System
- [ ] Global story flags (`SavedData` — world-persistent)
- [ ] Main story events: all online players force-joined automatically
- [ ] 5-minute accept timer; reschedule on timeout / mass decline
- [ ] Resolve Phase 4 edge case: side event lock vs. main story force-join

## Phase 5 — Horde Director
- [ ] Spawn pressure director
- [ ] Horde state machine (Dormant → Roaming → Tracking → Attacking → Dispersing)
- [ ] Limb system design decision (custom entity vs. capability attachment)

## Phase 6 — NPC System
- [ ] Evaluate Forge 1.18.2-compatible NPC mod
- [ ] Spawn/despawn API integration
- [ ] Event NPC hooks