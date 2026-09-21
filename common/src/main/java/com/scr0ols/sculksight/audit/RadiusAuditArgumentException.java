package com.scr0ols.sculksight.audit;

/** A radius-audit argument that Brigadier accepted and this mod does not. */
public final class RadiusAuditArgumentException extends Exception {

	private static final long serialVersionUID = 1L;

	public RadiusAuditArgumentException(String message) {
		super(message);
	}
}
