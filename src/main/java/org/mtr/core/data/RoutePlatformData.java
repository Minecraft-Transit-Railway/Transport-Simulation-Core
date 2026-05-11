package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.mtr.core.generated.data.RoutePlatformDataSchema;
import org.mtr.core.serializer.ReaderBase;

/**
 * One entry inside a {@link Route}'s ordered platform list: the id of the {@link Platform}
 * the route stops at, plus an optional per-stop custom destination string that overrides the
 * route's default destination at that platform.
 *
 * <p>The {@link #platform} reference is resolved by the simulator after deserialisation
 * &mdash; on disk only the platform id is persisted.</p>
 */
public final class RoutePlatformData extends RoutePlatformDataSchema {

	@Nullable
	public Platform platform;

	public RoutePlatformData(long platformId) {
		super(platformId);
	}

	public RoutePlatformData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Nullable
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
