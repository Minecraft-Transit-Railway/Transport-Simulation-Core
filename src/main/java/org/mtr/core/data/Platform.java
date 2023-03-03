package org.mtr.core.data;

import org.mtr.core.tools.Position;

public class Platform extends SavedRailBase<Platform, Station> {

	public Platform(long id, TransportMode transportMode, Position pos1, Position pos2) {
		super(id, transportMode, pos1, pos2);
	}

	public Platform(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}
}
