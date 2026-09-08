package com.scr0ols.sculksight.timing;

/** Discrete travel-delay classes used by the optional shell heatmap. */
public enum DelayBand {
	OUT_OF_RANGE(0x3A3A46), OCCLUDED(0x5A2A5A), ONE_TO_TWO(0x28B8D8),
	THREE_TO_FOUR(0x54D65A), FIVE_TO_EIGHT(0xF0D34A), NINE_TO_SIXTEEN(0xF08038),
	SEVENTEEN_PLUS(0xE63C4A);

	private final int colour;

	DelayBand(int colour) { this.colour = colour; }

	public int colour() { return colour; }

	public static DelayBand fromTicks(int ticks) {
		if (ticks < 1) throw new IllegalArgumentException("ticks must be positive: " + ticks);
		if (ticks <= 2) return ONE_TO_TWO;
		if (ticks <= 4) return THREE_TO_FOUR;
		if (ticks <= 8) return FIVE_TO_EIGHT;
		if (ticks <= 16) return NINE_TO_SIXTEEN;
		return SEVENTEEN_PLUS;
	}
}
