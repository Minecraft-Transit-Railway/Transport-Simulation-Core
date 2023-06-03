package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.core.generated.RoutePlatformDataSchema;
import org.mtr.core.serializers.ReaderBase;

public final class RoutePlatformData extends RoutePlatformDataSchema {

	public Platform platform;

	RoutePlatformData(long platformId) {
		super(platformId);
	}

	public RoutePlatformData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Platform getPlatform() {
		return platform;
	}

	public String getCustomDestination() {
		return customDestination;
	}

	public void writePlatformCache(Route route, Long2ObjectOpenHashMap<Platform> platformIdMap) {
		platform = platformIdMap.get(platformId);
		if (platform != null) {
			platform.routes.add(route);
			platform.routeColors.add(route.getColor());
		}
	}
}
