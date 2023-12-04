package org.mtr.core.data;

import org.mtr.core.generated.data.PlatformSchema;
import org.mtr.core.integration.Integration;
import org.mtr.core.oba.Stop;
import org.mtr.core.oba.StopDirection;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;

public final class Platform extends PlatformSchema {

	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final IntAVLTreeSet routeColors = new IntAVLTreeSet();
	private final Long2ObjectOpenHashMap<Angle> anglesFromDepot = new Long2ObjectOpenHashMap<>();

	public Platform(Position position1, Position position2, TransportMode transportMode, Data data) {
		super(position1, position2, transportMode, data);
	}

	public Platform(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
		DataFixer.unpackPlatformDwellTime(readerBase, value -> dwellTime = value);
	}

	/**
	 * @deprecated for {@link Integration} use only
	 */
	@Deprecated
	public Platform(ReaderBase readerBase) {
		this(readerBase, Integration.getData());
	}

	public void setDwellTime(long dwellTime) {
		this.dwellTime = dwellTime;
	}

	public long getDwellTime() {
		return transportMode.continuousMovement ? 1 : Math.max(1, dwellTime);
	}

	public void setAngles(long depotId, Angle angle) {
		anglesFromDepot.put(depotId, angle);
	}

	public Stop getOBAStopElement(IntAVLTreeSet routesUsed) {
		Angle angle = null;
		for (final Angle checkAngle : anglesFromDepot.values()) {
			if (angle == null) {
				angle = checkAngle;
			} else if (angle != checkAngle) {
				angle = null;
				break;
			}
		}

		final LatLon latLon = new LatLon(getMidPosition());
		final String stationName = area == null ? "" : Utilities.formatName(area.getName());
		final Stop stop = new Stop(
				getHexId(),
				getHexId(),
				String.format("%s%s%s%s", stationName, !stationName.isEmpty() && !name.isEmpty() ? " - " : "", name.isEmpty() ? "" : "Platform ", name),
				latLon.lat,
				latLon.lon,
				EnumHelper.valueOf(StopDirection.NONE, angle == null ? "" : angle.getClosest45().toString())
		);

		routeColors.forEach(color -> {
			stop.addRouteId(Utilities.numberToPaddedHexString(color, 6));
			routesUsed.add(color);
		});

		return stop;
	}
}
