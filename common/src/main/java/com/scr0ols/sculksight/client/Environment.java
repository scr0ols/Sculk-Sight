package com.scr0ols.sculksight.client;

import java.nio.file.Path;

/** The loader-neutral seam this package reads loader facts through. */
public interface Environment {

	/** Whether the game is running in a development environment. */
	boolean isDevelopmentEnvironment();

	/** The game directory: the instance folder for an install, {@code run/} in a dev environment. */
	Path gameDir();

	/** The directory a loader puts mod configuration files in. */
	Path configDir();
}
