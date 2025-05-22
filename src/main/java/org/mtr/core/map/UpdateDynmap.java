package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.mtr.core.Main;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.simulation.Simulator;

public final class UpdateDynmap implements UpdateWebMap {

	private static DynmapCommonAPI dynmapCommonAPI;

	static {
		try {
			DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {

				@Override
				public void apiEnabled(DynmapCommonAPI dynmapCommonAPI) {
					UpdateDynmap.dynmapCommonAPI = dynmapCommonAPI;
					try {
						final MarkerAPI markerAPI = dynmapCommonAPI.getMarkerAPI();
						UpdateWebMap.readResource(STATION_ICON_PATH, inputStream -> markerAPI.createMarkerIcon(STATION_ICON_KEY, STATION_ICON_KEY, inputStream));
						UpdateWebMap.readResource(DEPOT_ICON_PATH, inputStream -> markerAPI.createMarkerIcon(DEPOT_ICON_KEY, DEPOT_ICON_KEY, inputStream));
					} catch (Exception e) {
						Main.LOGGER.error("", e);
					}
				}
			});
		} catch (
				Exception e) {
			Main.LOGGER.error("", e);
		}
	}

	public static void updateDynmap(Simulator simulator) {
		try {
			updateDynmap(simulator.dimension, simulator.stations, MARKER_SET_STATIONS_ID, MARKER_SET_STATIONS_TITLE, MARKER_SET_STATION_AREAS_ID, MARKER_SET_STATION_AREAS_TITLE, STATION_ICON_KEY);
			updateDynmap(simulator.dimension, simulator.depots, MARKER_SET_DEPOTS_ID, MARKER_SET_DEPOTS_TITLE, MARKER_SET_DEPOT_AREAS_ID, MARKER_SET_DEPOT_AREAS_TITLE, DEPOT_ICON_KEY);
		} catch (Exception e) {
			Main.LOGGER.error("", e);
		}
	}

	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void updateDynmap(String dimension, ObjectArraySet<T> areas, String areasId, String areasTitle, String areaAreasId, String areaAreasTitle, String iconKey) {
		if (dynmapCommonAPI != null) {
			final String worldId;
			switch (dimension) {
				case "minecraft/overworld":
					worldId = "world";
					break;
				case "minecraft/the_nether":
					worldId = "DIM-1";
					break;
				case "minecraft/the_end":
					worldId = "DIM1";
					break;
				default:
					worldId = dimension.replaceFirst("/", ":");
					break;
			}

			final MarkerAPI markerAPI = dynmapCommonAPI.getMarkerAPI();
			markerAPI.getPlayerSets().forEach(playerSet -> {
				Main.LOGGER.info(playerSet.getSetID());
				Main.LOGGER.info(playerSet.getPlayers().size());
			});
			markerAPI.getMarkerSets().forEach(playerSet -> {
				Main.LOGGER.info(playerSet.getMarkerSetID());
				Main.LOGGER.info(playerSet.getMarkers().size());
			});

			final MarkerSet markerSetAreas;
			MarkerSet tempMarkerSetAreas = markerAPI.getMarkerSet(areasId);
			markerSetAreas = tempMarkerSetAreas == null ? markerAPI.createMarkerSet(areasId, areasTitle, ObjectSet.of(markerAPI.getMarkerIcon(iconKey)), false) : tempMarkerSetAreas;
			markerSetAreas.getMarkers().forEach(marker -> {
				if (marker.getMarkerID().startsWith(worldId)) {
					marker.deleteMarker();
				}
			});

			final MarkerSet markerSetAreaAreas;
			MarkerSet tempMarkerSetAreaAreas = markerAPI.getMarkerSet(areaAreasId);
			markerSetAreaAreas = tempMarkerSetAreaAreas == null ? markerAPI.createMarkerSet(areaAreasId, areaAreasTitle, new ObjectArraySet<>(), false) : tempMarkerSetAreaAreas;
			markerSetAreaAreas.setHideByDefault(true);
			markerSetAreaAreas.getAreaMarkers().forEach(marker -> {
				if (marker.getMarkerID().startsWith(worldId)) {
					marker.deleteMarker();
				}
			});

			UpdateWebMap.iterateAreas(areas, (id, name, color, areaCorner1X, areaCorner1Z, areaCorner2X, areaCorner2Z, areaX, areaZ) -> {
				markerSetAreas.createMarker(worldId + id, name, worldId, areaX, 0, areaZ, markerAPI.getMarkerIcon(iconKey), false);
				final AreaMarker areaMarker = markerSetAreaAreas.createAreaMarker(worldId + id, name, false, worldId, new double[]{areaCorner1X, areaCorner2X}, new double[]{areaCorner1Z, areaCorner2Z}, false);
				areaMarker.setFillStyle(0.5, 0xFFFFFF & color.getRGB());
				areaMarker.setLineStyle(1, 1, 0xFFFFFF & color.darker().getRGB());
			});
		}
	}
}
