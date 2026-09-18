package com.scr0ols.sculksight.audit;

/**
 * A radius-audit argument that Brigadier accepted and this mod does not.
 *
 * <p><b>Checked, for the reason {@code JsonParseException} is checked</b> (ARCHITECTURE.md
 * section 11.1): every caller has to say what it does about a malformed request, and the one
 * caller that matters is a command whose answer is to print the message and stop.
 *
 * <p>Its message is player-facing. It names what was wrong and what the permitted values are,
 * because the player reading it is standing in a world with a chat line and no documentation.
 */
public final class RadiusAuditArgumentException extends Exception {

	private static final long serialVersionUID = 1L;

	public RadiusAuditArgumentException(String message) {
		super(message);
	}
}
