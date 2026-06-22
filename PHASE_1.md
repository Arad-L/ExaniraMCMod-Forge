# Phase 1 Execution — Foundation

**Goal**: Persistent character sheet with profession-based stats, lifestyle-driven backstory, multi-step creation screen on first login, and a display GUI.  
No event logic. No multiplayer hooks. Foundation only.

**Status**: 🟢 Complete — verified in-game 2026-06-07

> ⚠️ **Downport Note**: Originally developed on NeoForge 1.21.1 using `AttachmentType<T>`.  
> Downported to Forge 1.18.2 (MDK 40.3.0) using the Forge Capability system  
> (`ICapabilitySerializable` + `CapabilityToken`). All functionality preserved.

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1.1 | Set up mod package structure | 🟢 Complete | `character/`, `backstory/`, `event/`, `network/`, `client/`, `handlers/`, `item/`, `command/` |
| 1.2 | Register CharacterSheet Capability | 🟢 Complete | `CharacterSheetCapability.java` + `ExaniraCapabilityProvider.java`; attached via `PlayerCapabilityHandler` |
| 1.3 | Implement core stat model | 🟢 Complete | `Stat.java`, `Profession.java` presets, `CharacterSheet.java`; stat range 1–10 |
| 1.4 | Character creation data definitions | 🟢 Complete | `LifestyleOption`, `LifestyleQuestion`, `CharacterCreationDefs` (5 questions, 3 opts each) |
| 1.5 | Selection-based backstory generator | 🟢 Complete | `BackstoryGenerator` uses profession + Q1/Q3 flavor; random flavor pool per creation |
| 1.6 | Multi-step character creation screen | 🟢 Complete | `CharacterCreationScreen.java` — Escape-blocked, 8 professions + 5 questions + confirm step |
| 1.7 | Creation packets + server handler | 🟢 Complete | `OpenCharacterCreationPacket`, `CharacterCreationSubmitPacket`, `CharacterCreationHandler` |
| 1.8 | Character sheet display GUI + keybind | 🟢 Complete | `CharacterSheetScreen.java`, `C` key, registered via `ClientRegistry.registerKeyBinding()` in `FMLClientSetupEvent` |
| 1.9 | End-to-end test (creation + display) | 🟢 Complete | Creation flow, stats, and backstory verified in-game |

---

## 1.1 — Package Structure

```
com.exanira/
    character/
        CharacterSheet.java             — data model (stats + backstory + initialized flag)
        Stat.java                       — enum: STRENGTH, AGILITY, INTELLIGENCE, PERCEPTION, LEADERSHIP, SURVIVAL
        CharacterSheetCapability.java   — Capability<CharacterSheet> declaration (CapabilityToken)
        ExaniraCapabilityProvider.java  — ICapabilitySerializable; hosts CharacterSheet + PendingEventAttachment
        CharacterCreationDefs.java      — 5 lifestyle questions, 3 options each
        LifestyleOption.java            — record: buttonText, backstoryText, statBonuses
        LifestyleQuestion.java          — record: title, bodyText, options
        Profession.java                 — enum: 8 professions, each with 6 base stat values
    backstory/
        BackstoryGenerator.java         — template resolver
        BackstoryTemplate.java          — template string data holder
        BackstoryPools.java             — static phrase pools
    handlers/
        PlayerCapabilityHandler.java    — attaches ExaniraCapabilityProvider; copies caps on clone (respawn/dim change)
        PlayerLoginHandler.java         — server login event; sends OpenCharacterCreation or syncs sheet
        CharacterCreationHandler.java   — packet handler for CharacterCreationSubmitPacket
    network/
        CharacterSheetSyncPacket.java   — S→C: syncs full CharacterSheet (stats map + backstory)
        OpenCharacterCreationPacket.java — S→C: zero-payload; triggers CharacterCreationScreen on client
        CharacterCreationSubmitPacket.java — C→S: profession ordinal + lifestyle choice list
    client/
        CharacterSheetScreen.java       — PoseStack Screen; stats + backstory display
        ClientCharacterData.java        — client mirror of CharacterSheet (Map<Stat,Integer> + backstory)
        KeyBindings.java                — GLFW_KEY_C binding, "key.categories.exanira"
        ClientEventHandler.java         — ClientTickEvent handler; opens CharacterSheetScreen on keybind
    ExaniraMod.java                     — @Mod main class; registers items, handlers, network, listeners
    ExaniraModClient.java               — client-only init; registers keybinding + ClientEventHandler
```

---

## 1.2 — Register CharacterSheet Capability (Forge 1.18.2)

Forge 1.18.2 (40.x) uses `ICapabilityProvider` / `ICapabilitySerializable` instead of the old `@CapabilityInject` pattern. `@CapabilityInject` was removed in Forge 40.x; the correct declaration is:

```java
// CharacterSheetCapability.java
public class CharacterSheetCapability {

    public static final Capability<CharacterSheet> INSTANCE =
            CapabilityManager.get(new CapabilityToken<CharacterSheet>(){});

    public static LazyOptional<CharacterSheet> get(Player player) {
        return player.getCapability(INSTANCE);
    }
}
```

Both capabilities (`CharacterSheet` and `PendingEventAttachment`) are hosted in a single provider:

```java
// ExaniraCapabilityProvider.java — implements ICapabilitySerializable<CompoundTag>
public class ExaniraCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    private final CharacterSheet characterSheet = new CharacterSheet();
    private final PendingEventAttachment pendingEvent = new PendingEventAttachment();

    private final LazyOptional<CharacterSheet> characterSheetOpt = LazyOptional.of(() -> characterSheet);
    private final LazyOptional<PendingEventAttachment> pendingEventOpt = LazyOptional.of(() -> pendingEvent);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == CharacterSheetCapability.INSTANCE) return characterSheetOpt.cast();
        if (cap == PendingEventCapability.INSTANCE)   return pendingEventOpt.cast();
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() { ... } // writes "character" + "event" subtags

    @Override
    public void deserializeNBT(CompoundTag tag) { ... }
}
```

Provider is attached via `PlayerCapabilityHandler`:

```java
@SubscribeEvent
public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
    if (event.getObject() instanceof Player) {
        event.addCapability(new ResourceLocation("exanira", "player_capabilities"),
                new ExaniraCapabilityProvider());
    }
}
```

Registered via `MinecraftForge.EVENT_BUS.register(PlayerCapabilityHandler.class)` in `ExaniraMod` constructor.  
No explicit `Capability.register()` call is needed — `CapabilityManager.get(new CapabilityToken<T>(){})` handles declaration automatically.

**Implementation note**: `CharacterSheet` implements `INBTSerializable<CompoundTag>` with the **1.18.2 signature** (no `HolderLookup.Provider` parameter — that was added in 1.20.4+). Capability data is stored inside the player's own save file (`playerdata/UUID.dat`) synchronously, giving per-world isolation automatically.

---

## 1.3 — Core Stat Model

Six stats, each stored as a simple `int` in an `EnumMap<Stat, Integer>`. Base values set at character creation. Upgradeable in Phase 4+.

```java
public enum Stat {
    STRENGTH("Strength"),
    AGILITY("Agility"),
    INTELLIGENCE("Intelligence"),
    PERCEPTION("Perception"),
    LEADERSHIP("Leadership"),
    SURVIVAL("Survival");
}
```

- Default base value: **1** (no stat starts at 0)
- Hard cap: **10**
- **Status**: 🟢 Complete

---

## 1.4 — MADLIBS Backstory Generator

Pure template engine. Runs once at character creation and stores the result as a `String` in `CharacterSheet`.

**Template**:
```
"You were a {profession} in {location}. You {physical_background}.
When you weren't working, you {hobby}.
When everything fell apart, you lost {loss}. Now you trust {distrust} the least."
```

- Profession + physical_background + hobby: **deterministic** from player choices
- Location + loss + distrust: **random** from `BackstoryPools` static lists (seeded per creation)
- Stored as `String backstory` inside `CharacterSheet` NBT

**Status**: 🟢 Complete

---

## 1.5 — Initialize Capability on First Login

Hook `PlayerEvent.PlayerLoggedInEvent` (Forge event, fires server-side on login).

```java
@SubscribeEvent
public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;

    CharacterSheetCapability.get(player).ifPresent(sheet -> {
        if (!sheet.isInitialized()) {
            // First login — prompt character creation
            ExaniraMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new OpenCharacterCreationPacket()
            );
        } else {
            // Returning player — restore items, resume events, sync sheet
            ExaniraItems.ensureRadio(player);
            EventQueueManager.INSTANCE.resyncPlayerIfMidEvent(player);
            ExaniraMod.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CharacterSheetSyncPacket(sheet)
            );
        }
    });
}
```

Registered via `MinecraftForge.EVENT_BUS.register(PlayerLoginHandler.class)` in `ExaniraMod` constructor.

**Status**: 🟢 Complete

---

## 1.6 — Character Sheet GUI Screen

`CharacterSheetScreen.java` — client-only `Screen` subclass. Displays all 6 stats plus the full backstory string (word-wrapped). No container, no server round-trip needed since stats are synced on login via `CharacterSheetSyncPacket`.

**Rendering**: Uses `PoseStack` (Forge 1.18.2 API). `GuiGraphics` (1.20+) not available.  
**Status**: 🟢 Complete

---

## 1.7 — Keybind to Open GUI

```java
// KeyBindings.java
public static final KeyMapping OPEN_CHARACTER_SHEET = new KeyMapping(
    "key.exanira.character_sheet",
    InputConstants.Type.KEYSYM,
    GLFW.GLFW_KEY_C,
    "key.categories.exanira"
);
```

- **Registration**: `ClientRegistry.registerKeyBinding(KeyBindings.OPEN_CHARACTER_SHEET)` called via `event.enqueueWork()` inside `FMLClientSetupEvent` listener on mod event bus (in `ExaniraModClient`)
- **Consumption**: `ClientEventHandler.onClientTick()` — checks `KeyBindings.OPEN_CHARACTER_SHEET.consumeClick()` during `ClientTickEvent` at `Phase.END`
- **Disconnect cleanup**: Same tick handler checks `mc.player == null` and clears `ClientEventState` when disconnected

> **1.18.2 note**: `RegisterKeyMappingsEvent` does not exist. `ClientRegistry.registerKeyBinding()` is the correct API. `ClientPlayerNetworkEvent.LoggingOut` may not resolve correctly — disconnect cleanup is handled via the null-player tick check instead.

**Status**: 🟢 Complete

---

## 1.8 — Networking (Forge 1.18.2)

Forge 1.18.2 uses `SimpleChannel` (from `NetworkRegistry.newSimpleChannel()`) for all custom packets. Registration happens in `FMLCommonSetupEvent`:

```java
// ExaniraMod.java
public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
    new ResourceLocation(MODID, "main"),
    () -> "1", "1"::equals, "1"::equals
);

// In commonSetup():
CHANNEL.registerMessage(id++,
    OpenCharacterCreationPacket.class,
    OpenCharacterCreationPacket::encode,
    OpenCharacterCreationPacket::decode,
    OpenCharacterCreationPacket::handle,
    Optional.of(NetworkDirection.PLAY_TO_CLIENT)  // required for S→C packets
);
// ... repeat for all 8 packets
```

> **NeoForge difference**: There is no `CustomPacketPayload` / `STREAM_CODEC` / `RegisterPayloadHandlersEvent` in Forge 1.18.2. Each packet needs `encode`, `decode`, and `handle` methods.  
> **Sending**: `ExaniraMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet)` for S→C.  
> `ExaniraMod.CHANNEL.sendToServer(packet)` for C→S.

**Status**: 🟢 Complete

---

## 1.9 — End-to-End Test Checklist

- [x] New player logs in → `CharacterSheet` capability is created and attached
- [x] `OpenCharacterCreationPacket` sent; character creation screen opens
- [x] All 8 professions + 5 questions + confirm step work correctly
- [x] Stats initialized from profession + lifestyle bonuses
- [x] Backstory string generated and non-empty
- [x] `CharacterCreationSubmitPacket` processed server-side; radio given; sheet synced to client
- [x] Data persists across server restart (capability NBT in playerdata/)
- [x] Press `C` → `CharacterSheetScreen` opens
- [x] GUI displays correct stat values from `ClientCharacterData`
- [x] GUI displays backstory text (word-wrapped)
- [ ] Player skin icon — not implemented, deferred
- [x] No errors in server or client logs during login or GUI open

**Status**: 🟢 Complete — all functional items verified in-game

---

## Open Questions (Resolved)

| # | Question | Resolution |
|---|----------|------------|
| Q1 | Stat value range? | 1–10 (min 1, max 10; `CharacterSheet.MAX_STAT = 10`) |
| Q2 | Backstory re-rollable? | No — locked at first login |
| Q3 | Character sheet title/name field? | No — just stats + backstory |

---

## Files Created in Phase 1

| File | Purpose |
|------|---------|
| `character/CharacterSheet.java` | Data model + `INBTSerializable<CompoundTag>` |
| `character/Stat.java` | Stat enum with display names |
| `character/CharacterSheetCapability.java` | `Capability<CharacterSheet>` via `CapabilityToken`; `get(Player)` helper |
| `character/ExaniraCapabilityProvider.java` | `ICapabilitySerializable`; hosts both capabilities in one provider |
| `character/CharacterCreationDefs.java` | 5 lifestyle questions, 3 options each |
| `character/LifestyleOption.java` | Record: buttonText, backstoryText, statBonuses |
| `character/LifestyleQuestion.java` | Record: title, bodyText, options list |
| `character/Profession.java` | Enum: 8 professions with base stat arrays |
| `backstory/BackstoryGenerator.java` | Template resolver |
| `backstory/BackstoryTemplate.java` | Template string data holder |
| `backstory/BackstoryPools.java` | Static phrase pools |
| `handlers/PlayerCapabilityHandler.java` | `AttachCapabilitiesEvent` + `PlayerEvent.Clone` for capability attachment/copy |
| `handlers/PlayerLoginHandler.java` | `PlayerEvent.PlayerLoggedInEvent` — sends creation or sync packet |
| `handlers/CharacterCreationHandler.java` | Packet handler for `CharacterCreationSubmitPacket` |
| `network/CharacterSheetSyncPacket.java` | S→C: stats map + backstory |
| `network/OpenCharacterCreationPacket.java` | S→C: zero-payload creation prompt |
| `network/CharacterCreationSubmitPacket.java` | C→S: profession ordinal + lifestyle choices |
| `client/CharacterSheetScreen.java` | PoseStack screen — stats + backstory |
| `client/ClientCharacterData.java` | Client-side mirror of CharacterSheet |
| `client/KeyBindings.java` | GLFW_KEY_C binding |
| `client/ClientEventHandler.java` | Tick handler — keybind + disconnect cleanup |
| `ExaniraMod.java` | Main mod class, network setup, event listeners |
| `ExaniraModClient.java` | Client-only init; keybinding via `FMLClientSetupEvent` |
| `src/main/resources/assets/exanira/lang/en_us.json` | Translations for GUI titles, item name, keybind |
