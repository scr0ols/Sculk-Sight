package com.scr0ols.sculksight.solver;

/**
 * The detection set: a dense bitset over the sensor's bounding cube, indexed by offset from
 * the sensor. Recorded as c-docs/DECISIONS.md ADR-016, specified in ARCHITECTURE.md section 3.1.
 *
 * <p><b>Why a bitset and not a set of positions.</b> Boundary-face extraction asks, for every
 * member, whether each of its six neighbours is also a member - roughly 12 600 lookups at
 * radius 8 and 102 000 at radius 16, using R2's confirmed counts of 2 109 and 17 077 in-range
 * positions. Here each lookup is an index computation, a shift and an and. The memory argument
 * runs the same way: the whole cube is 4 913 bits (615 bytes) at radius 8 and 35 937 bits
 * (4.5 KB) at radius 16, less than the object headers a hash set of 17 077 boxed positions
 * would need before storing anything.
 *
 * <p><b>Why the frame is sensor-relative.</b> ADR-014 fixed the cached vertices as
 * sensor-relative, so the offset that indexes this bitset is the same offset the mesh encoder
 * writes as a vertex coordinate. No translation step between them, and no world coordinate
 * anywhere in this package.
 *
 * <p>Not thread-safe, and not required to be: ARCHITECTURE.md section 6.2 requires that solves
 * for one sensor never run concurrently, and a set is written by exactly one solve.
 */
public final class DetectionSet {

	private final int radius;

	/** Side of the bounding cube, {@code 2 * radius + 1}. */
	private final int side;

	private final long[] words;

	public DetectionSet(int radius) {
		if (radius < 0) {
			throw new IllegalArgumentException("radius must not be negative: " + radius);
		}

		this.radius = radius;
		this.side = 2 * radius + 1;

		int bits = side * side * side;

		// Bits are packed 64 to a long, so the array holds ceil(bits / 64) of them. Written as
		// (bits + 63) / 64 rather than with Math.ceil because integer arithmetic gives the
		// exact answer and floating point would only be a way to get it wrong.
		this.words = new long[(bits + 63) / 64];
	}

	public int radius() {
		return radius;
	}

	/**
	 * The number of positions in the set - not the size of the cube.
	 *
	 * <p>Computed by popcount rather than kept in a counter field, so there is no invariant
	 * for {@link #add} to maintain and no way for the two to disagree. At radius 16 this is
	 * 562 {@code Long.bitCount} calls, each a single machine instruction on any CPU this runs
	 * on, and it is called once per solve rather than in a loop.
	 */
	public int size() {
		int count = 0;

		for (long word : words) {
			count += Long.bitCount(word);
		}

		return count;
	}

	/**
	 * Offsets are relative to the sensor block. Returns false for any offset outside
	 * {@code [-radius, radius]}.
	 *
	 * <p><b>The clamp outside the cube is exact, not merely convenient.</b> Boundary extraction
	 * queries the neighbours of cube-face members, which lie outside the cube. A neighbour
	 * outside is at offset {@code radius + 1} on some axis, so its squared distance is at least
	 * {@code (radius + 1)^2}, which is strictly greater than {@code radius^2} - genuinely
	 * outside the detection set by R2's own comparison. The clamp and the truth agree.
	 */
	public boolean contains(int dx, int dy, int dz) {
		if (isOutsideCube(dx, dy, dz)) {
			return false;
		}

		int bit = index(dx, dy, dz);

		// bit >> 6 is bit / 64, the word holding it; bit & 63 is bit % 64, its position inside
		// that word. Both work because 64 is a power of two, and both are what the JIT would
		// produce from the division and remainder anyway - written explicitly because this is
		// the idiom every bitset uses and it is worth being able to read it.
		return (words[bit >> 6] & (1L << (bit & 63))) != 0;
	}

	/**
	 * Adds a position to the set. Adding the same position twice is harmless.
	 *
	 * <p>Throws for an offset outside the cube, rather than ignoring it as {@link #contains}
	 * does. The asymmetry is deliberate: querying outside the cube is something boundary
	 * extraction does by design, while writing outside it can only be a bug in the caller,
	 * and silently dropping such a write would produce a shell with holes in it and no
	 * indication of why.
	 */
	public void add(int dx, int dy, int dz) {
		if (isOutsideCube(dx, dy, dz)) {
			throw new IndexOutOfBoundsException(
					"offset (" + dx + ", " + dy + ", " + dz + ") is outside the cube of radius " + radius);
		}

		int bit = index(dx, dy, dz);

		// 1L, not 1: shifting an int by 64 or more wraps the shift distance modulo 32 rather
		// than producing zero, so a 32-bit literal here would corrupt words silently.
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
