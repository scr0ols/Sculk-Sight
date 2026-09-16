# Installation

These instructions are for **Sculk Sight v0.2.0+26.2**, a pre-release for **Minecraft 26.2**. Choose the jar that matches the loader used by your *client* instance.

> [!IMPORTANT]
> Sculk Sight is client-side only. Do not install it on a server.

| Requirement | Fabric | NeoForge |
| --- | --- | --- |
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 25 |
| Loader | Fabric Loader 0.19.3 or newer | NeoForge 26.2.0.75 or newer |
| Additional mod | Fabric API 0.158.0+26.2 or newer | None |
| Sculk Sight jar | `fabric-sculksight-0.2.0+26.2.jar` | `neoforge-sculksight-0.2.0+26.2.jar` |

## Install the matching build

1. Prepare a Minecraft 26.2 client instance with Java 25 and either Fabric or NeoForge at the minimum version above.
2. If you chose Fabric, install the matching Fabric API in that instance. NeoForge does not need Fabric API.
3. Download the matching Sculk Sight jar from the [v0.2.0 release](https://github.com/scr0ols/Sculk-Sight/releases/tag/v0.2.0).
4. Put the jar in that instance's `mods` directory. Keep only the Sculk Sight jar for the loader and version you intend to run.
5. Launch the client. You can confirm initialization in the instance's `logs/latest.log`: the Fabric build writes `Sculk Sight client initialised.` and the NeoForge build writes `Sculk Sight (NeoForge) client initialised.`

If the game does not launch, follow [Troubleshooting](Troubleshooting).

## Compatibility and older releases

The `+26.2` suffix identifies the target Minecraft version of this build; it is **not** a claim of compatibility with a later Minecraft release. The current requirements above do not necessarily apply to older Sculk Sight builds. Use the notes attached to [v0.1.0](https://github.com/scr0ols/Sculk-Sight/releases/tag/v0.1.0) or [v0.0.1](https://github.com/scr0ols/Sculk-Sight/releases/tag/v0.0.1) if you need those historical pre-releases. In particular, v0.1.0 required Cloth Config, whereas v0.2.0 does not.
