# Changelog

All notable changes to Sculk Sight are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The project is in its proof-of-concept phase, whose single objective is to prove the solver is correct — that the shape the mod draws is the shape the game actually uses. Both exit criteria for that phase are now met; see Verified below.

## [0.0.1] - 2026-09-01 - proof of concept

**Published as a GitHub release only, and marked as a pre-release there.** The jar is `sculksight-0.0.1+26.2.jar`, built from this repository's `dev` branch with `gradlew clean build` on JDK 25, and attached to the tag `v0.0.1`. It is deliberately not listed on Modrinth or CurseForge: those pages, and the logo and icon they require, belong to the next phase, and a GitHub release needs none of them. What it does need is honest release notes about a build that draws one sensor at a time, with no automatic invalidation and no configuration, and those are on the release itself.

Publishing it is what made the versioning scheme due, and it was decided the same day rather than left implicit: **SemVer 2.0.0 core with the target Minecraft version as build metadata**, `MAJOR.MINOR.PATCH+MC`. The core tracks changes to the mod and the metadata tracks the game, so rebuilding unchanged mod code for a later game version is `0.0.1+26.3` rather than a new mod version. That distinction matters here because the manifest's version bound lives inside the jar, so a per-game-version rebuild always changes the jar even when no mod code has changed. Fabric API itself ships under this format.

The tag stays `v0.0.1`: under SemVer's own rules build metadata is ignored for comparison, so `0.0.1` and `0.0.1+26.2` are the same version and the suffix makes the target explicit rather than correcting anything. The pre-release flag on GitHub, not the number, is what marks this as a proof of concept.

No code changed for this release. The itemised entries below are left under `[Unreleased]` rather than moved up under this heading, because they describe the whole of the proof-of-concept phase's development and are not usefully split at this tag.

## [Unreleased]

### Added

- **The Fabric development environment for Minecraft 26.2**, at `fabric/`, derived from the official `fabric-example-mod` template (branch `26.2`). It builds the mod jar, generates decompiled Minecraft sources, and launches a dev client with the mod loaded. There is no mappings configuration, because 26.2 is unobfuscated — see the README's "About mappings" section. Toolchain versions were each looked up against Fabric's own sources rather than recalled, and are listed in the README.
- **`LICENSE` at the repository root**: GPL-3.0-or-later. The `jar` task packages it into the built jar.
- **`README.md` at the repository root**, covering what the mod does, what it does not do yet, the requirements for a player, and the build and development setup.
- **The client entrypoint** `com.scr0ols.sculksight.client.SculkSightClient`. It logged one line and did nothing else; it existed so the toolchain could be verified end to end before any mod code depended on it.
- **The solver.** `WorldView`, `Face`, `PositionFilter`, `DetectionSet`, `OcclusionTest`, `ShellSolver`, `BoundaryFaceSink` and `BoundaryFaceExtractor`, in `com.scr0ols.sculksight.solver`. The package names no Minecraft class at all — it reaches the world through the narrow `WorldView` interface, which is what makes it testable from a plain JVM test and portable to a second mod loader later. The solver runs synchronously; the threading model it is designed for is not implemented, because without a renderer there is nothing for a worker to hand off to.
- **The differential verification harness**, in `com.scr0ols.sculksight.verify`: `SensorProbe`, `Reaction`, `Outcome`, `VerificationSample`, `VerificationReport` and `DifferentialVerifier`. Everything above the probe seam was complete and tested before the probe itself existed, since how a vibration is triggered and observed was still an open question at that point.
- **JUnit on the Fabric build** (Jupiter 6.1.3 via `org.junit:junit-bom`), test scope only, with the version looked up on Maven Central rather than recalled.
- **The `WorldView` implementation over a real level**, `com.scr0ols.sculksight.client.LevelWorldView`. It takes a `BlockGetter` rather than a `ClientLevel`, because the vanilla segment test it delegates to is a `default` method on that interface. This is the only class in the mod that names the vibration-occlusion block tag.
- **The differential verification probe and command**, `com.scr0ols.sculksight.verify.IntegratedServerSensorProbe` and `VerificationCommand`. `/sculksight-verify <scene> [samples]` aims at a sculk sensor, solves against the **client** level, probes against the integrated server's level, and reports disagreements. It is registered only when `FabricLoader.getInstance().isDevelopmentEnvironment()` is true, and reaching server-side state is a development-environment concession that the shipped mod does not make — the dev-only gate is what keeps the client-side design honest.
- 39 unit tests covering the six-ray occlusion rule, the detection set, the solver, boundary-face extraction and the verification harness.
- **The renderer.** `ShellStyle` and `ShellQuad` in `com.scr0ols.sculksight.mesh` on the main source set, `ShellMeshBuilder` beside them on the client source set, and `ShellPipelines`, `SensorKey`, `ShellStats`, `ShellEntry`, `ShellUploadSlot`, `ShellBuffer` and `ShellRenderer` in `com.scr0ols.sculksight.client`. Aim at a sculk sensor and press **K**: the mod derives the radius through `GameEventListener.Provider`, solves, encodes one quad per boundary face into a sensor-relative mesh, uploads it once, and draws it twice per frame from that one buffer — a see-through pass with no depth test at α 0.10, then a depth-tested pass at α 0.25. Pressing again clears it. The key binding goes through Fabric's `KeyMappingHelper`, so it appears in the vanilla controls screen and can be rebound even though this version has no config of its own.
- **Only boundary faces are drawn** — one quad only where the neighbouring position is outside the set. That turns tens of thousands of positions into hundreds of quads, and it is also what makes the shape readable: the notch that wool carves out of the region is visible precisely because the shell has a surface there.
- **The see-through pipeline is built by the mod and ships no shader asset**, from `RenderPipelines.DEBUG_FILLED_SNIPPET` with the depth state cleared and its own identifier, naming vanilla's `core/position_color` on both stages. Both passes inherit their bind-group layout and their shader from that one snippet, which is a correctness requirement rather than tidiness: uniform blocks are matched **by name**, and a mismatch fails silently — the geometry draws in the wrong place with no error at all.
- **The drawn shell is checked against the solver's output rather than eyeballed.** Every boundary face is one quad and every quad is four vertices, so the face count `BoundaryFaceExtractor` produces and the vertex count `BufferBuilder` produces are related by an exact factor of four. `ShellRenderer` asserts that before uploading and **refuses to draw** when it fails. That is the project's governing principle applied literally: a shell that does not match the solver is the wrong shape, and drawing the wrong shape is worse than drawing nothing. Each solve also reports its predicted, occluded-out and boundary-face counts to chat, so a shell and a `/sculksight-verify` run on the same sensor print directly comparable numbers.
- 29 further unit tests, for the shell's quad geometry and the style, bringing the suite to 69. `ShellQuad` and `ShellStyle` are deliberately free of Minecraft types and live on the main source set, so the part of the encoder that can put the shell in the wrong place is reachable from a plain JVM test; `ShellMeshBuilder` names `BufferBuilder` and `MeshData` and cannot be.
- **A black outline on the shell's crease edges.** The shell reads as a soft amber fill, and the seam between what a sensor can detect and what it cannot was hard to pick out where two surfaces meet at an angle, which is exactly where the shape carved by a dampening block shows. That seam is now drawn as black lines, from a second cached buffer with its own two passes. Only the **creases** are outlined, the edges where the surface turns, not every block edge: outlining every block edge would put a one-block grid over the whole surface, which is a wireframe rather than a highlight. Lines keep a constant thickness on screen, so the seam stays legible from across a build.
- **The shell is more opaque when you are standing inside it.** It was not an illusion. The shell's faces are drawn from both sides, so looking at it from outside your view crosses the near wall and the far wall, two translucent layers; from inside, the near wall is behind you and only one layer is left, which is close to half the coverage. Each pass now corrects for that when the camera is inside the detection volume, so the shell looks the same from either side. Inside is tested against the detection set rather than against the radius, so standing in the shadow of a wool wall correctly counts as outside.
- 22 further unit tests, for the crease rule over all sixteen of its cases, the crease sweep, the edge geometry and the new opacity arithmetic, bringing the suite to 91.

### Changed

- **The renderer's Fabric hook is `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`**, chosen because it fires inside `LevelRenderer`'s main frame-graph pass: the camera matrix is on the model-view stack, terrain and its depth are complete, and clouds, weather and the post-processing chains have not run.
- **The solve runs on the client thread, not on a worker.** The worker-to-render hand-off is built and used exactly as designed — the encoder produces a `MeshData`, the producer offers it into `ShellUploadSlot` stamped with a revision, the render callback takes it and uploads — but the producer stays on the client thread, because whether a worker may safely read a live `ClientLevel` has not been established, and this project does not write unverified claims about Minecraft's behaviour into code. Moving the producer later is a change of submission site and nothing above it.
- **`ShellMeshBuilder.build` now takes the `ByteBufferBuilder` it encodes into as a caller-owned parameter, instead of allocating and closing one of its own.** See Fixed below for why.
- **Fabric Loom is pinned to the released `1.17.20`** rather than the template's `1.17-SNAPSHOT`, so that two checkouts of the same commit resolve the same plugin.
- **`ShellSolver.solveDetailed(...)` (both overloads) now returns `ShellSolution`**, pairing the accepted `DetectionSet` with a second `DetectionSet` of the positions the six-ray occlusion rule excluded. `ShellSolver.solve(...)`'s existing signatures are unchanged and now delegate to `solveDetailed(...)`, discarding the occluded-out half; no extra ray is cast either way.
- **`DifferentialVerifier.verify` stratifies its sample three ways instead of two.** It takes a `ShellSolution` rather than a plain `DetectionSet` and splits the sample across the new `PredictedClass` enum (`IN_SET` / `OCCLUDED_OUT` / `OUT_OF_RANGE`), so occluded positions get a dedicated share instead of being diluted into a much larger "everything else" bucket. A class smaller than its share is backfilled from the other two, so the fix costs no sample volume elsewhere. This matters because occlusion is the interesting half of the mod and the old split barely tested it — see Verified below.
- **`VerificationSample` carries a `PredictedClass` instead of a `predictedInSet` boolean**, and `VerificationReport` gained `inSetSampled` / `occludedOutSampled` / `outOfRangeSampled` counts, printed in `summary()`.
- **`/sculksight-verify <scene> [samples] [seed]`**: the seed is now an optional third argument, defaulting to the sensor's packed block position exactly as before when omitted, so a scene can be resampled at fresh positions without moving the sensor.
- **The mod version is now `0.0.1+26.2`** rather than `0.0.1`, adopting the versioning scheme described under `[0.0.1]` above.

### Removed

- **The GitHub Actions workflow that shipped with the template.** It assumes the Gradle build sits at the repository root, whereas this project's build sits at `fabric/`, so as shipped it would have been silently inert. CI belongs to the next phase; when it is set up it needs a `working-directory` or a `-p fabric` argument, and it must build every supported loader.

### Fixed

- **The renderer's first live frame crashed the client, and the cause was a resource-lifetime bug in the mesh encoder.** `ShellMeshBuilder.build` allocated its own `ByteBufferBuilder` and closed it in a `try`-with-resources before returning — but the returned `MeshData` is read later, on the render thread, by which point the builder that produced it had already been closed. `ByteBufferBuilder.close()` invalidates every `Result` it ever produced: it frees the native pointer and bumps an internal generation counter that every `Result.byteBuffer()` call checks. So the very first shell built a valid mesh, and the render thread's later read of it threw `IllegalStateException: Buffer is no longer valid`, crashing the client with a `ReportedException`. `MeshData.close()` — which `ShellUploadSlot`'s existing close discipline already calls at exactly the right points — does not free that memory; only the parent `ByteBufferBuilder`'s own `close()` does. `ShellMeshBuilder.build` now takes the `ByteBufferBuilder` as a parameter the caller owns, and `ShellRenderer` holds one long-lived, growable instance closed only when the client stops. This matches how vanilla's own `SectionCompiler` works: it receives its `ByteBufferBuilder`s from a pool it does not own, rather than allocating and closing its own per compile.

- **Speckling where the shell lies flat against a block face.** The shell's surfaces sit exactly on block boundaries, so a shell face flush against the underside of a wool block was at precisely the same depth as that block and the two fought over which one to draw, pixel by pixel. The shell's depth-tested pass now asks to be treated as very slightly nearer than whatever it is flush with, which is the same remedy the game uses for its own outlines. The see-through pass never needed it, since it does not compare depth at all.

### Notes on what the unit tests do and do not establish

JUnit tests validate the solver against this project's model of Minecraft, not against Minecraft itself. If the model is wrong, the solver, the tests and the oracle are all consistently wrong and all green — which is exactly why the differential verification below exists and is treated as the real gate. The one unit test that owes nothing to anyone's model of the game is the open-air position count, compared against the integer lattice counts of 2 109 at radius 8 and 17 077 at radius 16.

### Verified

**The first exit criterion is met.** Differential verification was run live on 2026-09-01 against a normal sculk sensor at `x=13, y=108, z=-1`, radius 8:

| Scene | Predicted in set | Sampled | Conclusive | Agree | Disagree | Inconclusive |
|---|---:|---:|---:|---:|---:|---:|
| `open-air` | 2 109 | 200 | 200 | 200 | **0** | 0 |
| `wool` | 1 955 | 200 | 200 | 200 | **0** | 0 |
| `sphere-corner` | 2 109 | 2 000 | 2 000 | 2 000 | **0** | 0 |

2 400 conclusive samples, three scenes, zero disagreements, against a requirement of at least 200 across at least 3.

**What it does not establish, stated because the headline number invites over-reading.** At the time of that run, sampling was stratified in two classes, so only about five of the wool scene's samples are expected to have landed on an occluded position: the run tested the range check two hundred times and the carved shape roughly five. That weakness in the instrument has since been fixed — the sampler now stratifies three ways, and a re-run of the wool scene tests occlusion 67 times.

**The second exit criterion — "the rendered shell matches the solver's output" — is now met.** Run live on 2026-09-01 across the same three scenes, on the OpenGL backend, which is where a threading mistake in the buffer path throws rather than racing silently:

- **open-air and sphere-corner** (same sensor as the differential-verification run above, radius 8): the shell's own reported numbers (`2109` positions in range, `0` occluded out) matched `/sculksight-verify`'s exactly, and both a 200-sample and a 2000-sample verification run against that sensor came back clean with zero disagreements.
- **wool**: closing a 3×3 wool cap over the sensor and then opening a hole in its centre moved the shell's `predicted` and `occluded-out` counts by exactly 14 in opposite directions (1955/154 → 1969/140) and visibly opened a matching notch in the drawn shell. This is the one check that can actually falsify the encoder, since a dropped or duplicated boundary face shows here and not in open air.
- The two-pass compositing reads as intended: the part of the shell with direct line of sight to the camera is visibly reinforced, and in game the detectable/non-detectable boundary corresponds to where the shell is drawn.
- Coplanar shell/terrain faces do speckle (z-fighting), confirmed by eye — a cosmetic artifact, not a criterion failure, and on the list to fix.

Getting to this result required the fix above.

### Not yet built

- ~~The mesh encoder and the renderer~~ — built 2026-09-01, run and verified the same day (see Verified above and Fixed).
- ~~Sensor targeting outside the verification command, and the key binding~~ — done; `ShellRenderer`, bound to **K**.
- ~~The `SensorProbe` implementation~~ — done 2026-09-01.
- **Invalidation.** The rules are designed but their notification channel is unresolved: which client-side hook reports a block change, and whether it exists without mixins on every supported loader. So the mod does not notice a block changing inside the region. Pressing the key twice re-solves, which is the interaction the wool scene needs anyway.
- **The worker executor**, blocked on establishing what a worker thread may safely read from a live `ClientLevel` rather than merely deferred.
- **Automatic sensor discovery**, needed for any mode that shows more than the one sensor you are aiming at.
