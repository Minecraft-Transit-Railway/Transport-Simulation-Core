package org.mtr.core.data;

import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Position;

public class Platform extends SavedRailBase<Platform, Station> {

	public Platform(long id, TransportMode transportMode, Position pos1, Position pos2) {
		super(id, transportMode, pos1, pos2);
	}

	public <T extends ReaderBase<U, T>, U> Platform(T readerBase) {
		super(readerBase);
	}
}
