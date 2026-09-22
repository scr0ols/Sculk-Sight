package com.scr0ols.sculksight.solver;

/** A dense bitset over the sensor's bounding cube, indexed by offset from the sensor. */
public final class DetectionSet {

	private final int radius;

	private final int side;

	private final long[] words;

	public DetectionSet(int radius) {
		if (radius < 0) {
			throw new IllegalArgumentException("radius must not be negative: " + radius);
		}

		this.radius = radius;
		this.side = 2 * radius + 1;

		int bits = side * side * side;

		this.words = new long[(bits + 63) / 64];
	}

	public int radius() {
		return radius;
	}

	/** The number of positions in the set, not the size of the cube. */
	public int size() {
		int count = 0;

		for (long word : words) {
			count += Long.bitCount(word);
		}

		return count;
	}

	/** Whether the sensor-relative offset is a member, false outside {@code [-radius, radius]}. */
	public boolean contains(int dx, int dy, int dz) {
		if (isOutsideCube(dx, dy, dz)) {
			return false;
		}

		int bit = index(dx, dy, dz);

		return (words[bit >> 6] & (1L << (bit & 63))) != 0;
	}

	/** Adds a position to the set, harmlessly if it is already a member. */
	public void add(int dx, int dy, int dz) {
		if (isOutsideCube(dx, dy, dz)) {
			throw new IndexOutOfBoundsException(
					"offset (" + dx + ", " + dy + ", " + dz + ") is outside the cube of radius " + radius);
		}

		int bit = index(dx, dy, dz);

		words[bit >> 6] |= 1L << (bit & 63);
	}

	private boolean isOutsideCube(int dx, int dy, int dz) {
		return dx < -radius || dx > radius
				|| dy < -radius || dy > radius
				|| dz < -radius || dz > radius;
	}

	private int index(int dx, int dy, int dz) {
		return (dz + radius) * side * side + (dy + radius) * side + (dx + radius);
	}
}
