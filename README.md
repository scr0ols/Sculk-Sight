# Sculk Sight 

Sculk Sight is a client-side Minecraft mod that makes sculk-sensor detection easier to understand. It can draw the effective area from which a vibration can reach a sensor and tell you when your current position can be detected by a nearby sensor.

Current pre-release: **v0.2.0** for **Minecraft 26.2**, with **Fabric and NeoForge** support.

Licence: **GPL-3.0-or-later**. See [LICENSE](LICENSE).

## Features

### Mode A — detection shell

Aim at a sculk sensor, calibrated sculk sensor, or sculk shrieker and press **K** (rebindable under **Options → Controls → Miscellaneous**) to track it. Sculk Sight calculates the effective detection area of each enabled tracked sensor and draws either one bounded union or separate shells, coloured by detector type: amber for a sculk sensor, light blue for a calibrated sculk sensor, and dark red for a shrieker. Press **G** to toggle all sensor rendering without changing per-sensor toggles.

The shell reflects the sensor's radius and vibration-dampening blocks such as wool and wool carpet, so dampened positions are absent rather than merely hidden. It is rendered with both see-through and depth-tested passes to remain readable in terrain.

Press **K** on another sensor to track it too, up to eight at once. Each tracked sensor's name, enabled toggle, and **Remove tracked render** control live in the settings screen below.

With sensor rendering active, press **H** to show the travel delay in ticks for the first enabled tracked sensor. Labels use the player's current view for visibility; sensor-occluded positions show no label, since the sensor cannot detect a vibration there.

### Mode C — detection indicator

Press **J** (also rebindable) to toggle the detection indicator. While enabled, it checks the loaded sculk sensors around you and sends a client-side chat message whenever your detected/not-detected state changes.

This mode answers “am I detected?” without drawing a shell. It only knows about sensors in the client’s loaded world data.

### Settings

Sculk Sight has an in-game settings screen with a **Shell opacity** slider, a **Mode** choice (Union or Split) for multi-sensor rendering, **Global render**/**Delay overlay**/**Detection indicator** toggles, and a bounded **Tracked sensors** list whose compact sensor cards keep identity, naming, enabled state, and removal together. Settings are saved between sessions in `config/sculksight.json`.

- Press **B** (rebindable) to open the settings screen directly from gameplay, on either loader.
- On **NeoForge**, you can also open the mod’s configuration from the Mods screen.
- On **Fabric**, you can also install [Mod Menu](https://modrinth.com/mod/modmenu) to open the configuration screen from its mod list. Mod Menu is optional; without it, edit `config/sculksight.json` manually or use the **B** key above.

## Requirements

| Component | Fabric | NeoForge |
|---|---|---|
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 25 |
| Loader | Fabric Loader 0.19.3 or newer | NeoForge 26.2.0.75 or newer |
| Required dependencies | Fabric API 0.158.0+26.2 or newer | None |

Sculk Sight is **client-side only**. Do not install it on a server.

## Install

1. Install Minecraft 26.2, Java 25, and either Fabric or NeoForge.
2. **Fabric only:** install Fabric API. NeoForge needs no extra dependency.
3. Download the matching v0.2.0 jar from [Releases](https://github.com/scr0ols/Sculk-Sight/releases) and place it in your instance's `mods` directory:
   - `fabric-sculksight-0.2.0+26.2.jar` for Fabric.
   - `neoforge-sculksight-0.2.0+26.2.jar` for NeoForge.
4. Launch the game. Aim at detectors and use **K** to track them, **G** to toggle rendering, then **H** for delay labels, or press **J** for the detection indicator. Press **B** to open the settings screen directly.

## Build from source

This is a root multi-project Gradle build:

```
common/     shared solver, rendering, configuration, and detection logic
fabric/     Fabric entry points and packaging
neoforge/   NeoForge entry points and packaging
```

Run commands from the repository root with JDK 25 available:

```bash
# Build and test all modules.
bash gradlew build

# Run the unit tests.
bash gradlew test

# Launch a development client for one loader (requires a graphical desktop).
bash gradlew :fabric:runClient
bash gradlew :neoforge:runClient
```

Built jars are written to `fabric/build/libs/` and `neoforge/build/libs/`.

## Known limitations

- Mode A tracks at most eight sensors at once (`MAX_TRACKED_SENSORS`); tracking a ninth is refused rather than replacing an existing one.
- The delay overlay (**H**) shows travel-delay labels for only the first enabled tracked sensor, even when several are tracked and enabled at once.
- Mode C reports whether any indexed, loaded sensor can detect you; it does not identify a particular sensor.
- The released mod is client-only. Development-only verification commands are not included in normal production use.

## Contributing and issues

Bug reports and source code are welcome at the [GitHub repository](https://github.com/scr0ols/Sculk-Sight). For project-specific build and test notes, see [AGENTS.md](AGENTS.md).
