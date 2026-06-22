# Phase 2 Execution — Event Engine (Minimum)

**Goal**: JSON-driven events that fire via operator command, show dialogue in a proper GUI with hard stat gates, and give the player a Radio item to open the event screen.  
No automatic triggers. No multiplayer party logic. Single-player event flow only.

**Status**: 🟢 Complete — verified in-game 2026-06-07

> ⚠️ **Downport Note**: Originally developed on NeoForge 1.21.1.  
> Downported to Forge 1.18.2 (MDK 40.3.0). Key API changes in this phase:  
> - `DataComponents.CUSTOM_DATA` / `CustomData` → `stack.getTag()` / `stack.getOrCreateTag()` for Radio NBT  
> - `PacketDistributor.sendToPlayer(player, packet)` → `CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet)`  
> - `player.sendSystemMessage(msg)` → `player.sendMessage(msg, Util.NIL_UUID)`  
> - `ClientPlayerNetworkEvent.LoggingOut` → tick-based null-player check in `ClientEventHandler`

| # | Task | Status | Notes |
|---|------|--------|-------|
| 2.1 | Event data model | 🟢 Complete | `EventType`, `EventChoice`, `EventDefinition`, `ActiveEvent`, `EventScene` in `event/` package |
| 2.2 | JSON event loader | 🟢 Complete | `EventLoader` — `SimplePreparableReloadListener`, reads `data/exanira/events/*.json`, refreshes on `/reload` |
| 2.3 | `EventQueueManager` | 🟢 Complete | Singleton; owns active event map, player→event map, stat gate checks, radio flag logic, reconnect resync |
| 2.4 | Event trigger (command) | 🟢 Complete | `/exanira event start <id>` and `/exanira event stop [<player>]` — op level 2; wired via `RegisterCommandsEvent` |
| 2.5 | Stat gate hard checks | 🟢 Complete | Server-side re-validation in `resolveChoice()`; requirement shown as `[PER 3+]` prefix on all choices |
| 2.6 | Network packets | 🟢 Complete | `EventStartPacket` (S→C), `EventChoicePacket` (C→S), `EventEndPacket` (S→C) |
| 2.7 | Server-side choice handler | 🟢 Complete | `EventChoiceHandler` — delegates to `EventQueueManager`; guards against stat spoofing |
| 2.8 | Radio item | 🟢 Complete | `RadioItem` + `ExaniraItems`; given on login via `ensureRadio()`; glows (enchantment foil) when event active |
| 2.9 | Event screen GUI | 🟢 Complete | `EventScreen` — proper `Screen` subclass, captures mouse, shows dialogue + choice buttons, Escape-closable |
| 2.10 | Client event state | 🟢 Complete | `ClientEventState` — mirrors active event; read by `RadioItem` and `EventScreen` |
| 2.11 | Offline reconnect resync | 🟢 Complete | `resyncPlayerIfMidEvent()` — re-sends `EventStartPacket` on login if player was mid-event |
| 2.12 | Locked choice tooltip | 🟢 Complete | Hover over locked button shows `lockedText` via `Screen.renderTooltip()` (replaces 1.19+ `Tooltip.create()`) |
| 2.13 | Event JSON files | 🟢 Complete | 7 event JSON files in `data/exanira/events/` |
| 2.14 | Radio texture | 🟢 Complete | `assets/exanira/textures/item/radio.png` (16×16); model at `assets/exanira/models/item/radio.json` |
| 2.15 | End-to-end test | 🟢 Complete | `/exanira event start abandoned_radio_station`, verify glow, open screen, test locked + unlocked choices |

---

## Design Decisions Made in Phase 2

| Decision | Choice | Rationale |
|---|---|---|
| Event trigger | Command-only (`/exanira event start <id>`) | Simplest for testing; automatic triggers deferred to Phase 3+ |
| Choice UI | Proper `Screen` (mouse-capturing) via right-click Radio | HUD overlay discarded — unusable without mouse capture |
| Event chaining (`successEvent`) | Fully implemented | Terminal choices with no `nextScene` can chain to a new event via `successEvent` |
| Locked choices | Visible, greyed-out, hover shows `lockedText` | Narrative tension; player knows what they're missing |
| Stat display | `[STAT N+]` prefix shown on all choices, met or not | Players learn what skills matter even when they pass |
| Tooltip implementation | `Screen.renderTooltip()` with `isMouseOver()` loop in `render()` | No `Tooltip.create()` in 1.18.2; manual hover detection replaces it |

---

## Files Created in Phase 2

```
event/
    EventType.java              — MAIN / SIDE / AMBIENT enum
    EventChoice.java            — single choice record (text, requires, lockedText, outcome, successEvent, nextScene)
    EventDefinition.java        — full parsed event record (id, type, npc, startScene, scenes map)
    EventScene.java             — scene record (id, dialogue, choices, successEvent)
    ActiveEvent.java            — running instance (participants, resolved flag, vote map, scene tracking)
    EventLoader.java            — reload listener; parses data/exanira/events/*.json
    EventQueueManager.java      — singleton; all event spawn/resolve/cleanup/resync logic

handlers/
    EventChoiceHandler.java     — packet handler for EventChoicePacket

command/
    ExaniraCommands.java        — /exanira event start/stop/invite/accept

network/
    EventStartPacket.java       — S→C: instanceKey + dialogue + List<ChoiceData> (text, available, lockedText, requirementText)
    EventChoicePacket.java      — C→S: instanceKey + choiceIndex
    EventEndPacket.java         — S→C: instanceKey (signals client to close screen + clear state)

item/
    RadioItem.java              — right-click opens EventScreen; isFoil() = "active" boolean in stack NBT tag
    ExaniraItems.java           — DeferredRegister<Item>; ensureRadio() utility

client/
    ClientEventState.java       — client mirror of active event (instanceKey, dialogue, choices, vote data)
    EventScreen.java            — Screen subclass; scrollable dialogue + choice buttons + tooltip for locked choices
    EventChoiceButton.java      — Button subclass; label left-aligned, vote count right, selection highlight overlay, lockedTooltip field

src/main/resources/
    data/exanira/events/
        abandoned_radio_station.json
        the_barricated_house.json
        the_doctor.json
        the_empty_campsite.json
        the_last_inhaler.json
        the_midnight_fire.json
        the_missing_family.json
    assets/exanira/models/item/
        radio.json              — item model (parent: item/generated; layer0: exanira:item/radio)
    assets/exanira/textures/item/
        radio.png               — 16×16 radio item texture
    assets/exanira/lang/
        en_us.json              — all GUI titles, item names, keybind labels
```

---

## Bug Fixes Applied Post-Phase-2

| Bug | Root Cause | Fix |
|---|---|---|
| Scene advance locks the event | `active.markResolved()` was called unconditionally before the `nextScene` check — every subsequent `resolveChoice()` returned early at the `isResolved()` guard | Moved `markResolved()` to only the terminal path (no `nextScene` + no `-1` dismiss) |
| Event state leaks between singleplayer worlds | `EventQueueManager.INSTANCE` is a JVM-level static; its maps survive server stop | `ServerStoppedEvent` → `shutdownAll()` (server-side); tick-based null-player check → `ClientEventState.clear()` (client) |
| SavedData persistence failures (Windows) | `ExaniraEventSavedData` used async atomic write (temp file → rename). On Windows, the rename threw `AccessDeniedException` silently, leaving old data on disk. | Replaced with `PendingEventAttachment` capability — data written directly into player NBT by Minecraft's synchronous player save. No temp files. Per-world automatically. |

---

## Known Implementation Notes (Forge 1.18.2 vs NeoForge 1.21.1)

| Concern | NeoForge 1.21.1 | Forge 1.18.2 (current) |
|---|---|---|
| Radio glow flag | `stack.get(DataComponents.CUSTOM_DATA)` | `stack.getTag()` / `stack.getOrCreateTag()` |
| Send S→C packet | `PacketDistributor.sendToPlayer(player, packet)` | `CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet)` |
| Send C→S packet | `PacketDistributor.sendToServer(packet)` | `CHANNEL.sendToServer(packet)` |
| Chat message to player | `player.sendSystemMessage(msg)` | `player.sendMessage(msg, Util.NIL_UUID)` |
| Component factory | `Component.literal(...)` / `Component.translatable(...)` | `new TextComponent(...)` / `new TranslatableComponent(...)` |
| Screen rendering | `GuiGraphics` | `PoseStack` |
| Button factory | `Button.builder(...).bounds(...).build()` | `new Button(x, y, w, h, component, onPress)` |
| Tooltip | `btn.setTooltip(Tooltip.create(Component.literal(...)))` | Manual `renderTooltip()` in `render()` via `isMouseOver()` loop |

---

## Phase 3 Preview — Multiplayer Logic

- Temporary party formation (all players assigned to same event instance)
- Event locking (player can only be in one side event at a time)
- Shared decision resolution + party vote UI (vote counts on buttons, persistent selection highlight)
- `successEvent` chain (fully implemented)
- Multi-scene events via `nextScene` (fully implemented)
- Automatic triggers (proximity / time-based / world-flag) — deferred to Phase 4+
