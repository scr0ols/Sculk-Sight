# Sculk Sight 

Sculk Sight is a client-side Minecraft mod that makes sculk-sensor detection easier to understand. It can draw the effective area from which a vibration can reach a sensor and tell you when your current position can be detected by a nearby sensor.

Current pre-release: **v0.1.0** for **Minecraft 26.2**, with **Fabric and NeoForge** support.

Licence: **GPL-3.0-or-later**. See [LICENSE](LICENSE).

## Features

### Mode A — detection shell

Aim at a sculk sensor, calibrated sculk sensor, or sculk shrieker and press **K** (rebindable under **Options → Controls → Miscellaneous**). Sculk Sight calculates its effective detection area and draws it as a translucent shell, coloured by the detector type: amber for a sculk sensor, light blue for a calibrated sculk sensor, and dark red for a shrieker.

The shell reflects the sensor's radius and vibration-dampening blocks such as wool and wool carpet, so dampened positions are absent rather than merely hidden. It is rendered with both see-through and depth-tested passes to remain readable in terrain.

Only one detection shell is active at a time. Press **K** again to clear it. If the world changes inside the displayed area, clear and re-enable the shell to calculate it again.

With a shell active, press **H** to show the travel delay in ticks at every in-range block position, including air. Labels use the player's current view for visibility; sensor-occluded positions remain labelled in purple so they are distinguishable from positions the sensor can detect.

### Mode C — detection indicator

Press **J** (also rebindable) to toggle the detection indicator. While enabled, it checks the loaded sculk sensors around you and sends a client-side chat message whenever your detected/not-detected state changes.

This mode answers “am I detected?” without drawing a shell. It only knows about sensors in the client’s loaded world data.

### Settings

Sculk Sight has an in-game settings screen with a **Shell opacity** slider and a **Multi-sensor rendering** choice (Union or Per-sensor). The latter has no effect yet — it configures a v0.2 multi-sensor renderer that has not shipped. Settings are saved between sessions in `config/sculksight.json`.

- On **NeoForge**, open the mod’s configuration from the Mods screen.
- On **Fabric**, install [Mod Menu](https://modrinth.com/mod/modmenu) to open the configuration screen from its mod list. Mod Menu is optional; without it, edit `config/sculksight.json` manually.

## Requirements

| Component | Fabric | NeoForge |
|---|---|---|
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 25 |
| Loader | Fabric Loader 0.19.3 or newer | NeoForge 26.2.0.75 or newer |
| Required dependencies | Fabric API 0.158.0+26.2 or newer; Cloth Config 26.2.155 or newer | Cloth Config 26.2.155 or newer |

Sculk Sight is **client-side only**. Do not install it on a server.

## Install

1. Install Minecraft 26.2, Java 25, and either Fabric or NeoForge.
2. Install the required dependencies for that loader:
   - **Fabric:** Fabric API and Cloth Config.
   - **NeoForge:** Cloth Config for NeoForge.
3. Download the matching v0.1.0 jar from [Releases](https://github.com/scr0ols/Sculk-Sight/releases) and place it in your instance's `mods` directory:
   - `fabric-sculksight-0.1.0+26.2.jar` for Fabric.
   - `neoforge-sculksight-0.1.0+26.2.jar` for NeoForge.
4. Launch the game. Aim at a sculk sensor and use **K** for the shell, then **H** for its delay labels, or press **J** for the detection indicator.

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

- Mode A displays one aimed sensor at a time and does not automatically recalculate after nearby world changes.
- Mode C reports whether any indexed, loaded sensor can detect you; it does not identify a particular sensor.
- The released mod is client-only. Development-only verification commands are not included in normal production use.

## Contributing and issues

Bug reports and source code are welcome at the [GitHub repository](https://github.com/scr0ols/Sculk-Sight). For project-specific build and test notes, see [AGENTS.md](AGENTS.md).
