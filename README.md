# Sculk Sight

A client-side Minecraft mod that draws the **effective detection surface** of a sculk sensor as a world overlay: the set of positions a vibration can actually reach the sensor from, including the irregular shape carved out of it by vibration-dampening blocks.

Detection range is not a cube, and it is rarely a clean sphere either. The range test is a distance check, so builders who plan around a bounding box lose the corners; and wool and other dampening blocks cut pieces out of the shape that no amount of mental arithmetic will recover. Those two facts are what this mod exists to show.

**Status: proof of concept (v0.0).** It works, and what it draws has been checked against the game rather than against expectation — see [Is the shape correct?](#is-the-shape-correct) below. It is also deliberately minimal: one sensor at a time, no configuration, and a shell that does not refresh itself when the world changes. Read [What it does not do yet](#what-it-does-not-do-yet) before installing, so that the gaps are not surprises.

Licence: **GPL-3.0-or-later**. See [LICENSE](LICENSE).

---

## What it does

Aim at a sculk sensor and press **K**. The mod reads the sensor's detection radius from the block itself, works out every block position that could trigger it, and draws the boundary of that region as a translucent amber shell. Press **K** again to clear it.

The shell is drawn in two passes: a faint see-through pass, so the whole shape stays legible through terrain, and a stronger depth-tested pass, so the part of the shell you can actually see is anchored in the world rather than floating over it. Dampened positions are simply absent from the shell, so wool above a sensor reads as a bite taken out of the shape.

Each press also prints one line to your chat with the numbers behind the shell: how many positions are in range, how many were cut out by dampening, and how many faces were drawn. That line is local to your client; nothing is sent to the server.

The keybind is registered as an ordinary Minecraft key mapping, so it appears in **Options → Controls → Miscellaneous** as *Toggle detection shell* and can be rebound there, even though this version has no configuration screen of its own.

## What it does not do yet

Stated plainly, because a visualiser you cannot trust is worse than no visualiser at all.

- **One sensor at a time.** Aiming at a second sensor replaces the first shell.
- **The shell does not update itself.** If you place or break a block inside the region after drawing it, the shell stays stale until you press **K** twice to re-solve. This is the biggest limitation of this build.
- **No "am I detected?" indicator.** You get the shape, not a live readout of whether you are standing inside it.
- **No configuration.** Colour, opacity and render distance are all fixed.
- **Only the normal sculk sensor has been verified.** The mod derives the radius generically, so aiming at a calibrated sensor or a shrieker will draw something — but only the normal sensor's shape has been checked against the game, and until the others are, treat their output as unverified.
- **Fabric only.** NeoForge is planned, not present.
- **Coplanar faces speckle.** Where the shell lies exactly flush against a block face, the two surfaces fight over the same depth and flicker. Cosmetic, and on the list.
- **Tested in single player only.** The mod is client-side and needs nothing installed on a server, but it has not yet been run against a multiplayer server.

## Requirements

| | |
|---|---|
| Minecraft | **26.2** exactly — the jar refuses to load on anything else, by design |
| Mod loader | Fabric Loader **0.19.3** or newer |
| Dependency | **Fabric API** |
| Java | **25** — required by Minecraft 26.2 itself, not by this mod |
| Side | **Client only.** There is nothing to install on a server |

The version bound is deliberately narrow. A mod that draws the wrong shape is worse than one that refuses to start, so this build claims support only for the version it was actually tested on.

## Install

1. Install **Fabric Loader** for Minecraft 26.2.
2. Download **Fabric API** for 26.2 and put it in your `mods` folder.
3. Download `sculksight-0.0.1.jar` from [Releases](https://github.com/scr0ols/Sculk-Sight/releases) and put it in the same folder.
4. Launch the game, load a world, aim at a sculk sensor and press **K**.

If nothing appears, check your chat: the mod says when you are not aiming at a block, and when the block you are aiming at carries no game event listener.

## Is the shape correct?

This is the question the whole project is built around, so the answer is not "it looked right".

The mod carries a development-only command that triggers a real vibration at a chosen position, watches whether the sensor actually reacted, and compares that against what the solver predicted for the same position — position by position, across a random sample. Disagreements are reported rather than smoothed over. Before this build was released, that comparison was run across three scenes — open air, a wool-occluded sensor, and the corners of the sphere, where a bounding-box assumption would fail — over 2 400 conclusive samples, with **zero disagreements**.

The drawn shell is then tied to the solver arithmetically rather than by eye: every boundary face is one quad and every quad is four vertices, so the two counts must stand in an exact 4:1 ratio. The renderer checks that before uploading anything and **refuses to draw** if it fails.

None of this makes the mod infallible. It makes it falsifiable, which is the most a visualiser can honestly offer.

---

## Building from source

The Gradle build lives in `fabric/`, not at the repository root.

```
LICENSE       GPL-3.0 text, packaged into the built jar
CHANGELOG.md
fabric/       the Fabric module - a self-contained Gradle build
```

`common/` and `neoforge/` will sit beside `fabric/` later. They do not exist yet: this phase is Fabric only, and shared code is extracted when duplication is concrete rather than anticipated.

### JDK 25

Minecraft 26.2 is compiled for Java 25 and the build will not work on anything older. `build.gradle` declares a Java 25 toolchain, which covers compilation and lets an IDE resolve the right JDK on its own — but Gradle itself, and the game launched by `runClient`, run on whatever JVM starts the build. So if your system `JAVA_HOME` points elsewhere, point it at a JDK 25 for this project.

PowerShell, per session:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
```

The alternative is `org.gradle.java.home` in `gradle.properties`, but that file is committed and a hardcoded machine path does not belong in it.

### Common tasks

Run these from inside `fabric/`.

Build the mod jar, into `fabric/build/libs/`:

```bash
./gradlew build
```

Decompile Minecraft so its source can be read in the IDE. Needed once per Minecraft version, and takes about a minute the first time:

```bash
./gradlew genSources
```

Launch the game with the mod loaded. The verification command `/sculksight-verify <scene> [samples] [seed]` is registered in this dev client only — it is gated on the development environment and is absent from the released jar:

```bash
./gradlew runClient
```

Run the unit tests on their own:

```bash
./gradlew test
```

### About mappings

There are none, and their absence is not a mistake.

Minecraft 26.1 was the first release Mojang shipped **unobfuscated**, and Fabric stopped maintaining Yarn after 1.21.11. So `build.gradle` has no `mappings` line, and the names you see in decompiled source are Mojang's own.

The practical consequence when reading Minecraft source: **class and member names from any 1.21-era tutorial, wiki page or forum post are Yarn names and do not apply here.** The relationship between the two naming schemes is a rename table, not something to guess at.

`genSources` is still required. The jar is unobfuscated but still compiled, so decompilation is still what turns it into readable source.

### Toolchain versions

| Component | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.158.0+26.2 |
| Fabric Loom | 1.17.20 |
| Gradle | 9.5.1 |
| Java | 25 |

All of these were looked up against Fabric's own sources on 2026-08-30 rather than recalled. Do not edit them from memory — 26.2 and its toolchain are newer than most documentation you will find.
