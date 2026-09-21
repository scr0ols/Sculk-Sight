package com.scr0ols.sculksight.config;

/** Thrown when a configuration file is not valid JSON or does not match the expected schema. */
public class JsonParseException extends Exception {

	public JsonParseException(String message) {
		super(message);
	}
}
