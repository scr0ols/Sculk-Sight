package com.scr0ols.sculksight.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * How a Fabric player reaches {@link ConfigScreens}.
 *
 * <p>Fabric ships no mod list of its own, so unlike NeoForge - whose loader offers
 * {@code IConfigScreenFactory} directly - there is no loader-provided route to a settings screen
 * here. Mod Menu is the ecosystem's answer, and this class is the whole of the integration: one
 * interface, one method, handing back the same loader-independent screen the NeoForge side asks for.
 *
 * <p><b>Soft, not required.</b> Mod Menu is a {@code compileOnly} dependency and a "recommends"
 * rather than a "depends" in {@code fabric.mod.json}. Fabric Loader only loads an entrypoint class
 * when something asks for that entrypoint's own key, and nothing asks for {@code modmenu} unless
 * Mod Menu itself is installed, so this class is never loaded - and its missing imports never
 * looked up - in a game without it. A player without Mod Menu therefore gets the whole mod and
 * hand-edits {@code config/sculksight.json} instead; the persistence layer beneath the screen
 * (PLAN.md section 4) is what makes that a real option rather than a shrug.
 */
public final class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreens::create;
	}
}
