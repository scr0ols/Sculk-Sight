# Sculk Sight 

Sculk Sight is a client-side Minecraft mod that makes sculk-sensor detection easier to understand. It can draw the effective area from which a vibration can reach a sensor and tell you when your current position can be detected by a nearby sensor.

Current pre-release: **v0.3.0** for **Minecraft 26.2**, with **Fabric and NeoForge** support.

Licence: **GPL-3.0-or-later**. See [LICENSE](LICENSE).

## Features

### Mode A - detection shell

Aim at a sculk sensor, calibrated sculk sensor, or sculk shrieker and press **K** (rebindable under **Options → Controls → Miscellaneous**) to track it. Sculk Sight calculates the effective detection area of each enabled tracked sensor and draws either one bounded union or separate shells, coloured by detector type: amber for a sculk sensor, light blue for a calibrated sculk sensor, and dark red for a shrieker. Press **G** to toggle all sensor rendering without changing per-sensor toggles.

The shell reflects the sensor's radius and vibration-dampening blocks such as wool and wool carpet, so dampened positions are absent rather than merely hidden. It is rendered with both see-through and depth-tested passes to remain readable in terrain.

Press **K** on another sensor to track it too, up to 32 at once. Each tracked sensor gets a **Remove** control on the settings screen below, plus an **Options** button that opens its name, enabled toggle, and delay-overlay checkbox.

With sensor rendering active, press **H** to show the travel delay in ticks for every tracked sensor whose own delay-overlay checkbox (in its Options screen) is on. Labels use the player's current view for visibility; sensor-occluded positions show no label, since the sensor cannot detect a vibration there.

### Mode B - find sensors around you

Run `/sculksight find <type> <radius> <mode>` to draw every detector around you at once, without aiming at each one. It is a client-side command: it is typed in chat like any other, but never reaches the server, so it works on a vanilla server exactly as it does in single-player.

| Argument | Values |
|---|---|
| `type` | `all`, `sensor`, `calibrated`, `shrieker` |
| `radius` | 1 to 512 blocks |
| `mode` | `static` or `live` |

All three are required, and the mode is the important one, because it picks between two different jobs:

- **`static`** - finds the sensors once, where you are standing, and adds them to the **Tracked sensors** list, exactly as if you had aimed at each one and pressed **K**. They are saved to `config/sculksight.json`, they each get a name and their own Enabled and Remove controls, and they stay put when you walk away. This is the one for auditing a redstone build you are working on.
- **`live`** - an x-ray that follows you. It re-selects every tick against wherever you are now, so shells appear and disappear as you move. Nothing is saved; leaving the world or running another find clears it.

A live find appears in the settings screen under a **Live find** heading, one row per sensor with a **Shown** toggle, so you can switch off an individual shell without cancelling the whole find. Those toggles last for the session. The heading also carries a **Pin all** button, which turns the current selection into tracked sensors and ends the live find - the same result as having run the find with `static`, for when you would rather walk around and look first. A sensor you have separately tracked with **K** and then disabled stays hidden even when a live find selects it too.

How many sensors one find may draw is capped (32 by default) so a large radius in a busy world cannot ask for an unbounded amount of work; the command says so when it truncates.

### Mode C - detection indicator

Press **J** (also rebindable) to toggle the detection indicator. While enabled, it checks the loaded sculk sensors around you and sends a client-side chat message whenever your detected/not-detected state changes.

This mode answers “am I detected?” without drawing a shell. It only knows about sensors in the client’s loaded world data.

### Settings

Sculk Sight has an in-game settings screen with a **Shell opacity** slider, a **Mode** choice (Union or Split) for multi-sensor rendering, **Global render**/**Delay overlay**/**Detection indicator** toggles, and a bounded **Tracked sensors** list whose compact sensor cards keep identity, naming, enabled state, and removal together. While a `live` find is running, a **Live find** section lists its current selection below the tracked sensors, with a per-sensor **Shown** toggle and a **Pin all** button. Settings are saved between sessions in `config/sculksight.json`; the live-find section is session-only and saves nothing until you pin it.

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
3. Download the matching v0.3.0 jar from [Releases](https://github.com/scr0ols/Sculk-Sight/releases) and place it in your instance's `mods` directory:
   - `fabric-sculksight-0.3.0+26.2.jar` for Fabric.
   - `neoforge-sculksight-0.3.0+26.2.jar` for NeoForge.
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

- The tracked-sensor list holds at most 32 sensors (`MAX_TRACKED_SENSORS`); tracking one more is refused rather than replacing an existing one, and a `static` find that selects more than will fit says how many it had to leave out.
- The delay overlay (**H**) only ever shows labels for tracked sensors; a sensor found through `/sculksight find` and never pinned to the tracked list has no delay-overlay checkbox and shows no labels.
- Mode C reports whether any indexed, loaded sensor can detect you; it does not identify a particular sensor.
- The released mod is client-only. Development-only verification commands are not included in normal production use.
