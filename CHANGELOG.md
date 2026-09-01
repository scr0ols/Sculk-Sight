# Changelog

All notable changes to Sculk Sight are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The project is in phase v0.0 (`c-docs/PLAN.md` §5), whose single objective is to prove the solver is correct. Both of v0.0's exit criteria are now met — see Verified below.

## [0.0.1] - 2026-09-01 - proof of concept

**Published as a GitHub release only, and marked as a pre-release there.** The jar is `sculksight-0.0.1+26.2.jar`, built from this repository's `dev` branch with `gradlew clean build` on JDK 25, and attached to the tag `v0.0.1`. It is not listed on Modrinth or CurseForge, and deliberately so: those pages, along with the logo and icon, are v0.1 deliverables (`c-docs/PLAN.md` §10) and a GitHub release needs none of them. What it does need is honest release notes about a build that does mode A only, one sensor at a time, with no automatic invalidation and no configuration, and those are on the release itself.

Publishing it is what made the versioning scheme due, and it was decided the same day rather than left implicit: **SemVer 2.0.0 core with the target Minecraft version as build metadata**, `MAJOR.MINOR.PATCH+MC`, with the minor component tracking the phase number from `c-docs/PLAN.md` §5. See `c-docs/DECISIONS.md` ADR-027. The tag stays `v0.0.1` — under SemVer's own rules `0.0.1` and `0.0.1+26.2` compare as the same version, since build metadata is ignored for comparison, so the suffix makes the target explicit rather than correcting anything. The pre-release flag on GitHub, not the number, is what carries the "this is a proof of concept" signal.

No code changed for this release. The itemised entries below are left under `[Unreleased]` rather than moved up under this heading, because they describe the whole of v0.0's development and are not usefully split at this tag.

## [Unreleased]

### Added

- **The Fabric development environment for Minecraft 26.2**, at `repo/fabric/`, derived from the official `fabric-example-mod` template (branch `26.2`). It builds to `sculksight-0.0.1.jar`, generates decompiled Minecraft sources, and launches a dev client with the mod loaded. There is no mappings configuration, because 26.2 is unobfuscated (`c-docs/RESEARCH-LOG.md` E1). Toolchain versions, and the sources each was looked up from rather than recalled, are in `c-docs/DECISIONS.md` ADR-011.
- **`LICENSE` at the repository root**: GPL-3.0-or-later, per ADR-010. The `jar` task packages it into the built jar.
- **`README.md` at the repository root**, covering the JDK 25 requirement, the common Gradle tasks, and why there is no mappings step.
- **The client entrypoint** `com.scr0ols.sculksight.client.SculkSightClient`. It logged one line and did nothing else; it existed so the toolchain could be verified end to end before any mod code depended on it.
- **The solver.** `WorldView`, `Face`, `PositionFilter`, `DetectionSet`, `OcclusionTest`, `ShellSolver`, `BoundaryFaceSink` and `BoundaryFaceExtractor`, in `com.scr0ols.sculksight.solver`, implementing the contracts fixed in `c-docs/ARCHITECTURE.md` §3.1, §3.2, §4.1, §4.2 and §4.4. The package names no Minecraft class, per ADR-018. The solver runs synchronously; the threading model of `ARCHITECTURE.md` §6.2 is not implemented, because without a renderer there is nothing for a worker to hand off.
- **The differential verification harness**, in `com.scr0ols.sculksight.verify`: `SensorProbe`, `Reaction`, `Outcome`, `VerificationSample`, `VerificationReport` and `DifferentialVerifier`. Everything above the probe seam is complete and tested. The probe itself is not implemented, and is blocked on R14. Recorded as ADR-020.
- **JUnit on the Fabric build** (Jupiter 6.1.3 via `org.junit:junit-bom`), test scope only, with the version looked up on Maven Central rather than recalled.
- **The `WorldView` implementation over a real level**, `com.scr0ols.sculksight.client.LevelWorldView`. Takes a `BlockGetter` rather than a `ClientLevel`, because `isBlockInLine` is a `default` method on that interface (R4 addendum). This is the only class in the mod that names the occlusion tag.
- **The differential verification probe and command**, `com.scr0ols.sculksight.verify.IntegratedServerSensorProbe` and `VerificationCommand`, unblocked by answering R14. `/sculksight-verify <scene> [samples]` aims at a sculk sensor, solves against the **client** level, probes against the integrated server's level, and reports disagreements. Registered only when `FabricLoader.getInstance().isDevelopmentEnvironment()` is true, per ADR-019 — the dev-only status is a gate, not an intention.
- 39 unit tests covering the six-ray rule, the detection set, the solver, boundary-face extraction and the verification harness.
- **The v0.0 renderer.** `ShellStyle` and `ShellQuad` in `com.scr0ols.sculksight.mesh` on the main source set, `ShellMeshBuilder` beside them on the client source set, and `ShellPipelines`, `SensorKey`, `ShellStats`, `ShellEntry`, `ShellUploadSlot`, `ShellBuffer` and `ShellRenderer` in `com.scr0ols.sculksight.client`. Together these implement `c-docs/ARCHITECTURE.md` §3.3, §3.4, §4.3, §6.2, §6.3, §6.4 and the whole of §7 outside the solver. Aim at a sculk sensor and press **K**: the mod derives the radius through `GameEventListener.Provider`, solves, encodes one quad per boundary face into a sensor-relative mesh, uploads it once, and draws it twice per frame from that one buffer — a see-through pass with no depth test at α 0.10, then a depth-tested pass at α 0.25 (ADR-021 through ADR-024). Pressing again clears it. The key binding goes through Fabric's `KeyMappingHelper`, so it appears in the vanilla controls screen and can be rebound even though v0.0 has no config of its own.
- **The see-through pipeline is built by the mod and ships no shader asset**, from `RenderPipelines.DEBUG_FILLED_SNIPPET` with the depth state cleared and its own identifier, naming vanilla's `core/position_color` on both stages. Both passes inherit their bind-group layout and their shader from that one snippet, which is a correctness requirement rather than tidiness: uniform blocks are matched **by name** and a mismatch fails silently, drawing in the wrong place with no error at all (R15.3).
- **The drawn shell is checked against the solver's output rather than eyeballed.** Every boundary face is one quad and every quad is four vertices, so the face count `BoundaryFaceExtractor` produces and the vertex count `BufferBuilder` produces are related by an exact factor of four. `ShellRenderer` asserts that before uploading and **refuses to draw** when it fails, which is `PLAN.md` §1 applied literally: a shell that does not match the solver is the wrong shape, and drawing the wrong shape is worse than drawing nothing. Each solve also reports its predicted, occluded-out and boundary-face counts to chat, so a shell and a `/sculksight-verify` run on the same sensor print comparable numbers.
- 29 further unit tests, for the shell's quad geometry and the v0.0 style, bringing the suite to 69. `ShellQuad` and `ShellStyle` are deliberately free of Minecraft types and live on the main source set, so the part of the encoder that can put the shell in the wrong place is reachable from a plain JVM test; `ShellMeshBuilder` names `BufferBuilder` and `MeshData` and cannot be.

### Changed

- **The renderer's Fabric hook is `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`** (ADR-025), chosen because it fires inside `LevelRenderer`'s main frame-graph pass — the camera matrix is on the model-view stack, terrain and its depth are complete, and clouds, weather and the post-processing chains have not run.
- **The v0.0 solve runs on the client thread, not on a worker** (ADR-026). `ARCHITECTURE.md` §6.2's hand-off is built and used exactly as specified — the encoder produces a `MeshData`, the producer offers it into `ShellUploadSlot` stamped with a revision, the render callback takes it and uploads — but the producer stays on the client thread, because whether a worker may read a live `ClientLevel` has not been established and `CONVENTIONS.md` §6 forbids writing that claim into code. Tracked as `OPEN-QUESTIONS.md` §15.
- **`ShellMeshBuilder.build` now takes the `ByteBufferBuilder` it encodes into as a caller-owned parameter, instead of allocating and closing one of its own.** See Fixed below for why.
- **Fabric Loom is pinned to the released `1.17.20`** rather than the template's `1.17-SNAPSHOT`, so that builds are reproducible (ADR-012).
- **`ShellSolver.solveDetailed(...)` (both overloads) now returns `ShellSolution`**, pairing the accepted `DetectionSet` with a second `DetectionSet` of the positions the six-ray occlusion rule excluded. `ShellSolver.solve(...)`'s existing signatures are unchanged and now delegate to `solveDetailed(...)`, discarding the occluded-out half; no extra ray is cast either way. `c-docs/ARCHITECTURE.md` §3.1/§4.2, `c-docs/DECISIONS.md` ADR-016's 2026-09-01 addendum.
- **`DifferentialVerifier.verify` stratifies its sample three ways instead of two.** It takes a `ShellSolution` rather than a plain `DetectionSet`, and splits the sample across the new `PredictedClass` enum (`IN_SET` / `OCCLUDED_OUT` / `OUT_OF_RANGE`), so the occlusion class gets a dedicated share instead of being diluted into a much larger "everything else" bucket (`c-docs/OPEN-QUESTIONS.md` §13). A class smaller than its share is backfilled from the other two, so the fix costs no sample volume elsewhere.
- **`VerificationSample` carries a `PredictedClass` instead of a `predictedInSet` boolean**, and `VerificationReport` gained `inSetSampled` / `occludedOutSampled` / `outOfRangeSampled` counts, printed in `summary()`.
- **`/sculksight-verify <scene> [samples] [seed]`**: the seed is now an optional third argument, defaulting to the sensor's packed block position exactly as before when omitted, so a scene can be resampled at fresh positions without moving the sensor.

### Removed

- **The GitHub Actions workflow that shipped with the template.** It assumes the Gradle build sits at the repository root, whereas ADR-013 puts it at `repo/fabric/`, so as shipped it would have been silently inert. CI is a v0.1 deliverable (`c-docs/PLAN.md` §10); when it is set up it needs a `working-directory` or a `-p fabric` argument, and it must build both loaders per ADR-002.

### Fixed

- **The renderer's first live frame crashed the client, and the cause was a resource-lifetime bug in the mesh encoder.** `ShellMeshBuilder.build` allocated its own `ByteBufferBuilder` and closed it in a `try`-with-resources before returning — but the returned `MeshData` is read later, across `ARCHITECTURE.md` §6.3's worker-to-render hand-off, by which point the builder that produced it had already been closed. `ByteBufferBuilder.close()` invalidates every `Result` it ever produced (it frees the native pointer and bumps an internal generation counter that every `Result.byteBuffer()` call checks), so the very first shell built a valid mesh and the render thread's later read of it threw `IllegalStateException: Buffer is no longer valid`, crashing the client with a `ReportedException`. `MeshData.close()` — which `ShellUploadSlot`'s existing close discipline already calls at exactly the right points — does not free this memory; only the parent `ByteBufferBuilder`'s own `close()` does. `ShellMeshBuilder.build` now takes the `ByteBufferBuilder` as a parameter the caller owns; `ShellRenderer` holds one long-lived, growable instance, closed only when the client stops, matching how vanilla's own `SectionCompiler` receives its `ByteBufferBuilder`s from a pool it does not own rather than allocating and closing its own per compile. `c-docs/RESEARCH-LOG.md` R15.6, `c-docs/DECISIONS.md` ADR-017's 2026-09-01 addendum.

### Notes on what these tests do and do not establish

Per `c-docs/TESTING-STRATEGY.md` §1, JUnit tests validate the solver against this project's model of Minecraft, not against Minecraft. The one exception is the open-air position count, which is compared against the integer lattice counts of 2 109 at radius 8 and 17 077 at radius 16 — arithmetic that owes nothing to anyone's model of the game. A differential verification run **has** now been performed — see below.


### Verified

**The first v0.0 exit criterion is met.** Differential verification was run live on 2026-09-01 against a normal sculk sensor at `x=13, y=108, z=-1`, radius 8:

| Scene | Predicted in set | Sampled | Conclusive | Agree | Disagree | Inconclusive |
|---|---:|---:|---:|---:|---:|---:|
| `open-air` | 2 109 | 200 | 200 | 200 | **0** | 0 |
| `wool` | 1 955 | 200 | 200 | 200 | **0** | 0 |
| `sphere-corner` | 2 109 | 2 000 | 2 000 | 2 000 | **0** | 0 |

2 400 conclusive samples, three scenes, zero disagreements, against the requirement of ≥ 200 across ≥ 3. Full analysis, including what the run does *not* establish, is `c-docs/RESEARCH-LOG.md` R14.

**What it does not establish, stated because the headline number invites over-reading.** Sampling is stratified in two classes, so only about five of the wool scene's samples are expected to have landed on an occluded position — the run tested the range check two hundred times and the carved shape roughly five. Tracked as `c-docs/OPEN-QUESTIONS.md` §13, since closed: the sampler now stratifies three ways and a re-run of the wool scene tests occlusion 67 times.

**The second v0.0 exit criterion — "the rendered shell matches the solver's output" — is now met.** Run live on 2026-09-01 across all three `TESTING-STRATEGY.md` §5 scenes, on the OpenGL backend as required (a threading mistake in the buffer path throws there and races silently on Vulkan, R13):

- **open-air and sphere-corner** (same sensor as the differential-verification run above, radius 8): the shell's own reported numbers (`2109` positions in range, `0` occluded out) matched `/sculksight-verify`'s exactly, and both a 200-sample and a 2000-sample verification run against that sensor came back clean with zero disagreements.
- **wool**: closing a 3×3 wool cap over the sensor and then opening a hole in its centre moved the shell's `predicted`/`occluded-out` counts by exactly 14 in opposite directions (1955/154 → 1969/140) and visibly opened a matching notch in the drawn shell — the one check that can actually falsify the encoder, since a dropped or duplicated boundary face shows here and not in open air.
- The two-pass compositing (ADR-021) reads as intended: the part of the shell with direct line of sight to the camera is visibly reinforced, and in-game the detectable/non-detectable boundary corresponds to where the shell is drawn.
- Coplanar shell/terrain faces do speckle (z-fighting), confirmed by eye — a cosmetic artifact, tracked as `c-docs/OPEN-QUESTIONS.md` §16 rather than a criterion failure.

Getting to this result required the fix above. Full evidence table in `c-docs/NEXT-STEPS.md` Step 12.

### Not yet built

- ~~The mesh encoder and the renderer~~ — built 2026-09-01, run and verified the same day (see Verified above and Fixed).
- ~~Sensor targeting outside the verification command, and the key binding~~ — done; `ShellRenderer`, bound to **K**.
- ~~The `SensorProbe` implementation~~ — done; R14 answered 2026-09-01.
- Invalidation. `ARCHITECTURE.md` §5's rules are written but their notification channel is R11 and is unanswered, so the mod does not notice a block changing inside the cube. Pressing the key twice re-solves, which is the interaction the wool scene needs anyway.
- The worker executor, blocked on `OPEN-QUESTIONS.md` §15 rather than merely deferred.
