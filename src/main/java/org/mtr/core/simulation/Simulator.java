package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.nio.file.Path;

public class Simulator implements Utilities {

	private long lastMillis = Main.START_MILLIS;
	private long currentMillis = Main.START_MILLIS;

	public final int millisPerGameDay;
	public final float startingGameDayPercentage;

	public final ObjectAVLTreeSet<Station> stations = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Platform> platforms = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Siding> sidings = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Depot> depots = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Lift> lifts = new ObjectAVLTreeSet<>();
	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails = new Object2ObjectOpenHashMap<>();
	public final SignalBlocks signalBlocks = new SignalBlocks();
	public final DataCache dataCache = new DataCache(this);
	private final FileLoader fileLoader;

	public Simulator(String dimension, Path rootPath, int millisPerGameDay, float startingGameDayPercentage) {
		this.millisPerGameDay = millisPerGameDay;
		this.startingGameDayPercentage = startingGameDayPercentage;

		fileLoader = new FileLoader(this, dimension, rootPath.resolve(dimension));
	}

	public void tick() {
		lastMillis = currentMillis;
		currentMillis = System.currentTimeMillis();
		fileLoader.autoSaveTick();
	}

	public void stop() {
		fileLoader.fullSave();
	}

	public boolean matchMillis(long millis) {
		final long previousDifference = Utilities.circularDifference(millis, lastMillis, MILLIS_PER_DAY);
		final long currentDifference = Utilities.circularDifference(currentMillis, millis, MILLIS_PER_DAY);
		return previousDifference > 0 && previousDifference < MILLIS_PER_DAY / 2 && currentDifference < MILLIS_PER_DAY / 2;
	}
}
