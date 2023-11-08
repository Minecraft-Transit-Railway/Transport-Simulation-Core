package org.mtr.core.oba;

public enum StopDirection {
	NONE, N, NE, E, SE, S, SW, W, NW;

	@Override
	public String toString() {
		return this == NONE ? "" : super.toString();
	}
}
