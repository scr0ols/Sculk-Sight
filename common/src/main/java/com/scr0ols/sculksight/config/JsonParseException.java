package com.scr0ols.sculksight.config;

/**
 * Thrown when a configuration file is not the JSON this mod wrote, or is JSON but not of the
 * shape the configuration schema expects.
 *
 * <p>A checked exception rather than an unchecked one, deliberately. Every caller is reading a
 * file a player may have hand-edited, so a malformed document is an expected input rather than a
 * programming error, and the compiler requiring each caller to say what it does about that is the
 * point.
 */
public class JsonParseException extends Exception {

	public JsonParseException(String message) {
		super(message);
	}
}
