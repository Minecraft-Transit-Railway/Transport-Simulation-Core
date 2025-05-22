package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.Main;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.tool.Utilities;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public interface UpdateWebMap {

	String MARKER_SET_STATIONS_ID = "mtr_stations";
	String MARKER_SET_STATION_AREAS_ID = "mtr_station_areas";
	String MARKER_SET_STATIONS_TITLE = "Stations";
	String MARKER_SET_STATION_AREAS_TITLE = "Station Areas";
	String MARKER_SET_DEPOTS_ID = "mtr_depots";
	String MARKER_SET_DEPOT_AREAS_ID = "mtr_depot_areas";
	String MARKER_SET_DEPOTS_TITLE = "Depots";
	String MARKER_SET_DEPOT_AREAS_TITLE = "Depot Areas";
	String STATION_ICON_PATH = "/assets/mtr/textures/block/sign/logo.png";
	String DEPOT_ICON_PATH = "/assets/mtr/textures/block/sign/logo_grayscale.png";
	String STATION_ICON_KEY = "mtr_station";
	String DEPOT_ICON_KEY = "mtr_depot";
	int ICON_SIZE = 24;

	static void readResource(String path, Consumer<InputStream> callback) {
		try (final InputStream inputStream = Main.class.getResourceAsStream(path)) {
			if (inputStream != null) {
				callback.accept(inputStream);
			}
		} catch (IOException e) {
			Main.LOGGER.error("", e);
		}
	}

	static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void iterateAreas(ObjectArraySet<T> areas, AreaCallback areaCallback) {
		areas.forEach(area -> {
			final double x1 = area.getMinX();
			final double z1 = area.getMinZ();
			final double x2 = area.getMaxX() + 1;
			final double z2 = area.getMaxZ() + 1;
			areaCallback.areaCallback(area.getHexId() + "_" + System.currentTimeMillis(), Utilities.formatName(area.getName()), new Color(area.getColor()), x1, z1, x2, z2, (x1 + x2) / 2, (z1 + z2) / 2);
		});
	}

	@FunctionalInterface
	interface AreaCallback {
		void areaCallback(String id, String name, Color color, double areaCorner1X, double areaCorner1Z, double areaCorner2X, double areaCorner2Z, double areaX, double areaZ);
	}
}
