package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.LatLon;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public class Platform extends SavedRailBase<Platform, Station> {

	public Platform(long id, TransportMode transportMode, Position pos1, Position pos2) {
		super(id, transportMode, pos1, pos2);
	}

	public <T extends ReaderBase<U, T>, U> Platform(T readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public JsonObject getOBAStopElement(DataCache dataCache, IntArraySet routesUsed) {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("id", getHexId());
		final LatLon latLon = new LatLon(getMidPosition());
		jsonObject.addProperty("lat", latLon.lat);
		jsonObject.addProperty("lon", latLon.lon);
		final String stationName = area == null ? "" : area.getFormattedName();
		jsonObject.addProperty("name", String.format("%s%s%s%s", stationName, !stationName.isEmpty() && !name.isEmpty() ? " - " : "", name.isEmpty() ? "" : "Platform ", name));
		jsonObject.addProperty("code", getHexId());
		jsonObject.addProperty("locationType", 0);
		final JsonArray jsonArray = new JsonArray();
		dataCache.platformIdToRouteColors.getOrDefault(id, new IntArraySet()).forEach(color -> {
			jsonArray.add(Utilities.numberToPaddedHexString(color, 6));
			routesUsed.add(color);
		});
		jsonObject.add("routeIds", jsonArray);
		return jsonObject;
	}
}
