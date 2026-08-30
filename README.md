# Sculk Sight

A client-side Minecraft mod that renders the **effective detection surface** of sculk sensors as a world overlay: the set of positions from which a vibration can actually reach the sensor, including the irregular shape carved out by dampening blocks.

**Status: pre-alpha, phase v0.0.** There is no mod logic yet. This repository currently holds a working Fabric development environment and a mod that loads and does nothing. The project is deliberately in a research stage — see the planning documents.

Planning, research and decision documents live outside this repository, in the workspace at `..\..\projects\minecraft\Sculk Sight\c-docs\` (reachable from that workspace as `Sculk Sight\repo`, a directory junction pointing here). `START-HERE.md` there is the entry point.

Licence: **GPL-3.0-or-later**. See [LICENSE](LICENSE).

---

## Layout

```
LICENSE      GPL-3.0 text, packaged into the built jar
fabric/      the Fabric module - a self-contained Gradle build
```

`common/` and `neoforge/` will sit beside `fabric/` later. They do not exist yet: phase v0.0 is Fabric only, and `common` is extracted when duplication is concrete rather than anticipated.

---

## Requirements

**JDK 25.** Minecraft 26.2 is compiled for Java 25 and the build will not work on anything older.

This machine has both JDK 21 and JDK 25 installed. `JAVA_HOME` points at **JDK 21** system-wide and was deliberately left that way, so that other projects here are not disturbed. That means Gradle has to be pointed at JDK 25 for this project specifically.

PowerShell, per session:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
```

`build.gradle` also declares a Java 25 toolchain, which covers compilation and lets an IDE resolve the right JDK on its own. The environment variable is still needed because Gradle itself, and the game launched by `runClient`, run on whatever JVM starts the build.

If you would rather not set it each time, the alternative is `org.gradle.java.home` in `gradle.properties` — but that file is committed and a hardcoded machine path does not belong in it.

---

## Common tasks

Run all of these from inside `fabric/`, with `JAVA_HOME` set as above.

Build the mod jar (output in `fabric/build/libs/`):

```bash
./gradlew build
```

Decompile Minecraft so its source can be read in the IDE. Needed once per Minecraft version; takes about a minute the first time:

```bash
./gradlew genSources
```

Launch the game with the mod loaded:

```bash
./gradlew runClient
```

---

## About mappings

There are none, and their absence is not a mistake.

Minecraft 26.1 was the first release Mojang shipped **unobfuscated**, and Fabric stopped maintaining Yarn after 1.21.11. So `build.gradle` has no `mappings` line, and the names you see in decompiled source are Mojang's own.

The practical consequence when reading Minecraft source: **class and member names from any 1.21-era tutorial, wiki page or forum post are Yarn names and do not apply here.** The relationship between the two naming schemes is a rename table, not something to guess at. Confirmed 26.2 names for this project's areas of interest are recorded in the research log in the planning workspace.

`genSources` is still required. The jar is unobfuscated but still compiled, so decompilation is still what turns it into readable source.

---

## Toolchain versions

| Component | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.158.0+26.2 |
| Fabric Loom | 1.17.20 |
| Gradle | 9.5.1 |
| Java | 25 |

All of these were looked up against Fabric's own sources on 2026-08-30 rather than recalled, and the sources are recorded alongside them in the project's decision log. Do not edit them from memory — 26.2 and its toolchain are newer than most documentation you will find.
