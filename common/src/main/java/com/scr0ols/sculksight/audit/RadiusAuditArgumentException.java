package com.scr0ols.sculksight.audit;

import net.minecraft.network.chat.Component;

/** A radius-audit argument that Brigadier accepted and this mod does not. */
public final class RadiusAuditArgumentException extends Exception {

	private static final long serialVersionUID = 1L;

	private final transient Component component;

	public RadiusAuditArgumentException(Component component) {
		this.component = component;
	}

	/** The player-facing explanation, as a translatable component with its arguments intact. */
	public Component component() {
		return component;
	}
}
