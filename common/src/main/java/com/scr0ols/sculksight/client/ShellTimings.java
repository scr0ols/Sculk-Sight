package com.scr0ols.sculksight.client;

record ShellTimings(long snapshotNanos, long encodeNanos, long uploadNanos) {

	long clientNanos() {
		return snapshotNanos + uploadNanos;
	}

	String summary() {
		return "client thread: snapshot " + TierTiming.millis(snapshotNanos) + " ms + upload "
				+ TierTiming.millis(uploadNanos) + " ms = " + TierTiming.millis(clientNanos())
				+ " ms of CPU (budget 2 ms per tick); worker: encode "
				+ TierTiming.millis(encodeNanos) + " ms, off the frame path.";
	}
}
