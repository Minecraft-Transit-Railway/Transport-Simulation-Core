package org.mtr.core.data;

import org.mtr.core.generated.data.SimplifiedRoutePlatformSchema;
import org.mtr.core.serializer.ReaderBase;

public class SimplifiedRoutePlatform extends SimplifiedRoutePlatformSchema {

	public SimplifiedRoutePlatform(long platformId, String destination, String stationName) {
		super(platformId, destination, stationName);
	}

	public SimplifiedRoutePlatform(ReaderBase readerBase) {
		super(readerBase);
	}

	public String getStationName() {
		return stationName;
	}

	public long getPlatformId() {
		return platformId;
	}

	public String getDestination() {
		return destination;
	}
}
