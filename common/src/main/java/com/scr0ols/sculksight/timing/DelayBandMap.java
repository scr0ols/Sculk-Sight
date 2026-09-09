package com.scr0ols.sculksight.timing;

import java.util.Arrays;

/** Source-cell ownership of delay bands for one sensor-relative solve. */
public final class DelayBandMap {
	private final int radius;
	private final byte[] bands;

	public DelayBandMap(int radius) {
		if (radius < 0) throw new IllegalArgumentException("radius must not be negative: " + radius);
		this.radius = radius;
		int cells = 2 * radius + 1;
		bands = new byte[cells * cells * cells];
		Arrays.fill(bands, (byte) DelayBand.OUT_OF_RANGE.ordinal());
	}

	public DelayBand bandAt(int dx, int dy, int dz) {
		if (outside(dx, dy, dz)) return DelayBand.OUT_OF_RANGE;
		return DelayBand.values()[bands[index(dx, dy, dz)]];
	}

	public void assign(int dx, int dy, int dz, DelayBand band) {
		if (outside(dx, dy, dz)) throw new IndexOutOfBoundsException("source cell outside map");
		bands[index(dx, dy, dz)] = (byte) band.ordinal();
	}

	public void assignDelay(int dx, int dy, int dz, int ticks) {
		assign(dx, dy, dz, DelayBand.fromTicks(ticks));
	}

	private boolean outside(int dx, int dy, int dz) {
		return dx < -radius || dx > radius || dy < -radius || dy > radius || dz < -radius || dz > radius;
	}

	private int index(int dx, int dy, int dz) {
		int side = 2 * radius + 1;
		return (dz + radius) * side * side + (dy + radius) * side + dx + radius;
	}
}
