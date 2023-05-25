package org.mtr.core.data;

import org.mtr.core.generated.RoutePlatformDataSchema;
import org.mtr.core.serializers.ReaderBase;

public final class RoutePlatformData extends RoutePlatformDataSchema {

	public RoutePlatformData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return "";
	}

	public long getPlatformId() {
		return platformId;
	}

	public String getCustomDestination() {
		return customDestination;
	}
}
