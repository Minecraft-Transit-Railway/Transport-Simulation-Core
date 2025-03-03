package org.mtr.core.data;

import org.mtr.core.generated.data.RoutePlatformDataSchema;
import org.mtr.core.serializer.ReaderBase;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class RoutePlatformData extends RoutePlatformDataSchema {

	public Platform platform;

	public RoutePlatformData(long platformId) {
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

	public void setCustomDestination(String customDestination) {
		this.customDestination = customDestination;
	}

	public void writePlatformCache(Route route, Long2ObjectOpenHashMap<Platform> platformIdMap) {
		platform = platformIdMap.get(platformId);
		if (platform != null) {
			platform.routes.add(route);
			platform.routeColors.add(route.getColor());
		}
	}
}
