package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.*;
import org.mtr.core.tools.Position;

import java.nio.file.Path;

public class Simulator {

	public final ObjectAVLTreeSet<Station> stations = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Platform> platforms = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Siding> sidings = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Depot> depots = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Lift> lifts = new ObjectAVLTreeSet<>();
	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails = new Object2ObjectOpenHashMap<>();
	public final SignalBlocks signalBlocks = new SignalBlocks();
	public final DataCache dataCache = new DataCache(stations, platforms, sidings, routes, depots, lifts);
	private final FileLoader fileLoader;

	public Simulator(String dimension, Path rootPath) {
		fileLoader = new FileLoader(this, dimension, rootPath.resolve(dimension));
		fileLoader.load();
	}

	public void tick() {
		fileLoader.autoSaveTick();
	}

	public void stop() {
		fileLoader.fullSave();
	}
}
