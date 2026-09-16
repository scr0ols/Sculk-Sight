# Installation

These instructions apply to **Sculk Sight v0.2.0+26.2** on **Minecraft 26.2**. Download the jar for your client's mod loader.

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

1. Set up a Minecraft 26.2 client instance with Java 25 and a supported version of Fabric Loader or NeoForge.
2. If you use Fabric, add Fabric API to the instance's `mods` directory. NeoForge does not require it.
3. Download the jar for your loader from the [v0.2.0 release](https://github.com/scr0ols/Sculk-Sight/releases/tag/v0.2.0).
4. Place the Sculk Sight jar in the same instance's `mods` directory. Do not install both loader variants together.
5. Launch the client. To check that the mod initialized, open the instance's `logs/latest.log` and look for `Sculk Sight client initialised.` (Fabric) or `Sculk Sight (NeoForge) client initialised.` (NeoForge).

If the game fails to launch, see [Troubleshooting](Troubleshooting).

## Compatibility and older releases

The `+26.2` suffix identifies the Minecraft version targeted by this build; it does not indicate support for later versions. For older builds, use the requirements in their release notes: [v0.1.0](https://github.com/scr0ols/Sculk-Sight/releases/tag/v0.1.0) or [v0.0.1](https://github.com/scr0ols/Sculk-Sight/releases/tag/v0.0.1). Version 0.1.0 required Cloth Config; version 0.2.0 does not.
