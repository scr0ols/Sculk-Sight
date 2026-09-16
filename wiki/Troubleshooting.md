# Troubleshooting installation and launch

This page covers installation and launch issues with v0.2.0. Check the [installation requirements](Installation) before following the steps below.

## Minecraft reports an incompatible version

Check that the instance uses **Minecraft 26.2** and **Java 25**. Confirm that the Sculk Sight jar has version `0.2.0+26.2` and matches the instance's loader. The `+26.2` suffix does not indicate support for other Minecraft versions.

## A dependency or loader is missing

For Fabric, check that the instance has Fabric Loader **0.19.3 or newer** and Fabric API **0.158.0+26.2 or newer**. For NeoForge, check that it has NeoForge **26.2.0.75 or newer**; Fabric API is not required. After correcting the installation, launch the client again.

## The wrong Sculk Sight jar is installed

The Fabric jar is named `fabric-sculksight-0.2.0+26.2.jar`; the NeoForge jar is `neoforge-sculksight-0.2.0+26.2.jar`. Check the instance's `mods` directory and keep only the jar that matches its loader. An older Sculk Sight jar may also cause a version conflict.

## The jar was installed on a server

Sculk Sight is client-side only. Move the jar out of the server's `mods` directory and install it in the player's client instance.

## The game still will not start

Open `logs/latest.log` in the game directory for the instance you launched. If Minecraft generated a crash report, check the same directory's `crash-reports` folder. Your launcher may use a separate game directory for each instance.

When [reporting an issue](https://github.com/scr0ols/Sculk-Sight/issues), include:

- Minecraft and Java versions
- Fabric Loader or NeoForge version, plus the Fabric API version if applicable
- Exact Sculk Sight jar filename
- Steps to reproduce the failure and the error shown
- Relevant log excerpt or crash report

Remove account names, access tokens, server addresses, and personal file paths before sharing logs.
