package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.LatLon;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public class Platform extends SavedRailBase<Platform, Station> {

	private Long2ObjectOpenHashMap<Angle> anglesFromDepot = new Long2ObjectOpenHashMap<>();

	public Platform(long id, TransportMode transportMode, Position pos1, Position pos2) {
		super(id, transportMode, pos1, pos2);
	}

	public <T extends ReaderBase<U, T>, U> Platform(T readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void setAngles(long depotId, Angle angle) {
		anglesFromDepot.put(depotId, angle);
	}

	public JsonObject getOBAStopElement(DataCache dataCache, IntArraySet routesUsed) {
		final JsonArray jsonArray = new JsonArray();
		dataCache.platformIdToRouteColors.getOrDefault(id, new IntArraySet()).forEach(color -> {
			jsonArray.add(Utilities.numberToPaddedHexString(color, 6));
			routesUsed.add(color);
		});

		Angle angle = null;
		for (final Angle checkAngle : anglesFromDepot.values()) {
			if (angle == null) {
				angle = checkAngle;
			} else if (angle != checkAngle) {
				angle = null;
				break;
			}
		}
		final String angleString = angle == null ? "" : angle.toString();

		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("code", getHexId());
		jsonObject.addProperty("direction", angleString.length() == 3 ? angleString.substring(2) : angleString);
		jsonObject.addProperty("id", getHexId());
		final LatLon latLon = new LatLon(getMidPosition());
		jsonObject.addProperty("lat", latLon.lat);
		jsonObject.addProperty("locationType", 0);
		jsonObject.addProperty("lon", latLon.lon);
		final String stationName = area == null ? "" : area.getFormattedName();
		jsonObject.addProperty("name", String.format("%s%s%s%s", stationName, !stationName.isEmpty() && !name.isEmpty() ? " - " : "", name.isEmpty() ? "" : "Platform ", name));
		jsonObject.add("routeIds", jsonArray);
		jsonObject.addProperty("wheelchairBoarding", "UNKNOWN");

		return jsonObject;
	}
}
