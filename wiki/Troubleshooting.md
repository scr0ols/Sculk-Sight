# Troubleshooting installation and launch

Start by checking the [v0.2.0 requirements](Installation). These checks cover installation and launch only; they do not diagnose in-game behavior.

## Minecraft rejects the mod or says its version is incompatible

Check that the instance runs **Minecraft 26.2** and **Java 25**, then confirm you installed the v0.2.0 jar for that instance's loader. The `+26.2` filename suffix does not make the jar compatible with other Minecraft versions. Once corrected, launch the client again.

## A dependency or loader is missing

For Fabric, use Fabric Loader **0.19.3 or newer** and Fabric API **0.158.0+26.2 or newer** for Minecraft 26.2. For NeoForge, use **26.2.0.75 or newer**; Fabric API is not a NeoForge dependency. Check the version reported by your launcher or loader, update the missing component, and relaunch.

## The wrong Sculk Sight jar is installed

The Fabric jar starts with `fabric-sculksight-`; the NeoForge jar starts with `neoforge-sculksight-`. Keep only the v0.2.0+26.2 jar matching your chosen loader in the instance's `mods` directory. Remove an older or wrong-loader Sculk Sight jar from that instance before trying again.

## The server rejects the mod

Sculk Sight is **client-side only**. Install it in the player's client instance, not the server's `mods` directory. If you maintain both, check which directory received the jar.

## The game still will not start

Open the affected instance's `logs/latest.log`. If Minecraft produced a crash report, check that instance's `crash-reports` directory too. Launcher layouts differ, so use the game directory configured for the instance you actually launched.

When [reporting an issue](https://github.com/scr0ols/Sculk-Sight/issues), include:

- Minecraft and Java versions;
- Fabric Loader or NeoForge version, and Fabric API version if using Fabric;
- the exact Sculk Sight jar filename;
- what happened, what you expected, and steps that reproduce it;
- a relevant log excerpt or crash report.

Remove account names, access tokens, server addresses, and personal filesystem paths before sharing logs. A launch failure can have several causes; these details help identify the actual one without assuming Sculk Sight is responsible.
