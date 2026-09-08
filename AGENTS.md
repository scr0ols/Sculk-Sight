# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.
- `common`'s test sourceSet has no Minecraft classpath at all (see `common/build.gradle`'s `neoForge { neoFormVersion = ... }` block, which only reaches the module's main compilation). A test that imports any `net.minecraft.*` type fails to compile. This is why every existing test under `common/src/test` avoids vanilla types entirely, going through project-defined plain-data stand-ins instead (e.g. `WorldPosition` for a Minecraft position, `WorldView`/`RecordingWorld` for a Minecraft level) - not a style preference, a hard build constraint. When a class under `common/src/main` needs a Minecraft-type method tested, extract the pure part into a sibling using only project types (see `IndexReconciliation` next to `SensorIndex.reconcile`, or `IndexVerifier` next to `SensorIndex` generally) and test that instead.
- No JDK ships in a fresh worktree/sandbox for this project, and `gradlew` may not be executable. A portable JDK (e.g. Temurin, matching `gradle.properties`' `java_version`) can be downloaded and pointed to via `JAVA_HOME`/`org.gradle.java.home` without root; invoke the wrapper as `bash gradlew ...` to avoid needing the executable bit at all. `./gradlew build` (which excludes the `runClient` tasks) compiles and tests `common`, `fabric`, and `neoforge` without a display; only `runClient` needs a real graphics stack.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
