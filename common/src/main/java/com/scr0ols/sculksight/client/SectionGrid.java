package com.scr0ols.sculksight.client;

record SectionGrid(int minSectionX, int minSectionY, int minSectionZ,
		int spanX, int spanY, int spanZ) {

	SectionGrid {
		if (spanX < 1 || spanY < 1 || spanZ < 1) {
			throw new IllegalArgumentException(
					"span must be at least 1 on every axis, was " + spanX + "x" + spanY + "x" + spanZ);
		}
	}

	static SectionGrid over(int minSectionX, int minSectionY, int minSectionZ,
			int maxSectionX, int maxSectionY, int maxSectionZ) {

		return new SectionGrid(minSectionX, minSectionY, minSectionZ,
				maxSectionX - minSectionX + 1,
				maxSectionY - minSectionY + 1,
				maxSectionZ - minSectionZ + 1);
	}

	int size() {
		return spanX * spanY * spanZ;
	}

	int index(int sectionX, int sectionY, int sectionZ) {
		int x = sectionX - minSectionX;
		int y = sectionY - minSectionY;
		int z = sectionZ - minSectionZ;

		if (x < 0 || x >= spanX || y < 0 || y >= spanY || z < 0 || z >= spanZ) {
			return -1;
		}

		return x + y * spanX + z * spanX * spanY;
	}

	int sectionXOf(int index) {
		return minSectionX + index % spanX;
	}

	int sectionYOf(int index) {
		return minSectionY + index / spanX % spanY;
	}

	int sectionZOf(int index) {
		return minSectionZ + index / (spanX * spanY);
	}
}
