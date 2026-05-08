package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.extern.log4j.Log4j2;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jspecify.annotations.Nullable;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.simulation.Simulator;

/**
 * Pushes the simulator's stations and depots to the Dynmap web map as markers and area overlays.
 *
 * <p>Loaded reflectively when Dynmap is on the classpath; the static initialiser registers a
 * {@link DynmapCommonAPIListener} that captures the {@link DynmapCommonAPI} once it becomes
 * available, then {@link #updateDynmap(Simulator)} can be called periodically to refresh markers.
 * All Dynmap interaction is best-effort — exceptions are logged but never propagated, so a
 * Dynmap outage does not stop the simulator.</p>
 */
@Log4j2
public final class UpdateDynmap implements UpdateWebMap {

	private static @Nullable DynmapCommonAPI dynmapCommonAPI;

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
						log.error("Failed to register Dynmap marker icons", e);
					}
				}
			});
		} catch (Exception e) {
			log.error("Failed to register Dynmap API listener", e);
		}
	}

	/**
	 * Refresh both station and depot marker sets for {@code simulator}'s dimension.
	 *
	 * @param simulator simulator whose stations and depots should be reflected on Dynmap
	 */
	public static void updateDynmap(Simulator simulator) {
		try {
			updateDynmap(simulator.dimension, simulator.stations, MARKER_SET_STATIONS_ID, MARKER_SET_STATIONS_TITLE, MARKER_SET_STATION_AREAS_ID, MARKER_SET_STATION_AREAS_TITLE, STATION_ICON_KEY);
			updateDynmap(simulator.dimension, simulator.depots, MARKER_SET_DEPOTS_ID, MARKER_SET_DEPOTS_TITLE, MARKER_SET_DEPOT_AREAS_ID, MARKER_SET_DEPOT_AREAS_TITLE, DEPOT_ICON_KEY);
		} catch (Exception e) {
			log.error("Failed to update Dynmap markers for {}", simulator.dimension, e);
		}
	}

	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void updateDynmap(String dimension, ObjectArraySet<T> areas, String areasId, String areasTitle, String areaAreasId, String areaAreasTitle, String iconKey) {
		if (dynmapCommonAPI != null) {
			final String worldId = switch (dimension) {
				case "minecraft/overworld" -> "world";
				case "minecraft/the_nether" -> "DIM-1";
				case "minecraft/the_end" -> "DIM1";
				default -> dimension.replaceFirst("/", ":");
			};

			final MarkerAPI markerAPI = dynmapCommonAPI.getMarkerAPI();

			final MarkerSet markerSetAreas;
			final MarkerSet tempMarkerSetAreas = markerAPI.getMarkerSet(areasId);
			markerSetAreas = tempMarkerSetAreas == null ? markerAPI.createMarkerSet(areasId, areasTitle, ObjectSet.of(markerAPI.getMarkerIcon(iconKey)), false) : tempMarkerSetAreas;
			markerSetAreas.getMarkers().forEach(marker -> {
				if (marker.getMarkerID().startsWith(worldId)) {
					marker.deleteMarker();
				}
			});

			final MarkerSet markerSetAreaAreas;
			final MarkerSet tempMarkerSetAreaAreas = markerAPI.getMarkerSet(areaAreasId);
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
