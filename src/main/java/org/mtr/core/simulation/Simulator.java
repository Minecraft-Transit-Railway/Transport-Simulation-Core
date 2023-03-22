package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashBigSet;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.tools.Utilities;

import java.nio.file.Path;

public class Simulator implements Utilities {

	private long lastMillis = Main.START_MILLIS;
	private long currentMillis = Main.START_MILLIS;
	private boolean autoSave = false;

	public final int millisPerGameDay;
	public final float startingGameDayPercentage;

	public final ObjectAVLTreeSet<Station> stations = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Platform> platforms = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Siding> sidings = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Depot> depots = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Lift> lifts = new ObjectAVLTreeSet<>();
	public final ObjectOpenHashBigSet<RailNode> railNodes = new ObjectOpenHashBigSet<>();
	public final SignalBlocks signalBlocks = new SignalBlocks();
	public final DataCache dataCache = new DataCache(this);

	private final String dimension;
	private final FileLoader<Station> fileLoaderStations;
	private final FileLoader<Platform> fileLoaderPlatforms;
	private final FileLoader<Siding> fileLoaderSidings;
	private final FileLoader<Route> fileLoaderRoutes;
	private final FileLoader<Depot> fileLoaderDepots;
	private final FileLoader<Lift> fileLoaderLifts;
	private final FileLoader<RailNode> fileLoaderRailNodes;

	public Simulator(String dimension, Path rootPath, int millisPerGameDay, float startingGameDayPercentage) {
		this.dimension = dimension;
		this.millisPerGameDay = millisPerGameDay;
		this.startingGameDayPercentage = startingGameDayPercentage;

		final Path savePath = rootPath.resolve(dimension);
		fileLoaderStations = new FileLoader<>(stations, Station::new, savePath.resolve("stations"), false);
		fileLoaderPlatforms = new FileLoader<>(platforms, Platform::new, savePath.resolve("platforms"), true);
		fileLoaderSidings = new FileLoader<>(sidings, Siding::new, savePath.resolve("sidings"), true);
		fileLoaderRoutes = new FileLoader<>(routes, Route::new, savePath.resolve("routes"), false);
		fileLoaderDepots = new FileLoader<>(depots, messagePackHelper -> new Depot(messagePackHelper, this), savePath.resolve("depots"), false);
		fileLoaderLifts = new FileLoader<>(lifts, Lift::new, savePath.resolve("lifts"), true);
		fileLoaderRailNodes = new FileLoader<>(railNodes, RailNode::new, savePath.resolve("rails"), true);

		dataCache.sync();
		stations.forEach(Station::init);
		platforms.forEach(Platform::init);
		sidings.forEach(Siding::init);
		routes.forEach(Route::init);
		depots.forEach(Depot::init);
		lifts.forEach(Lift::init);

		Main.LOGGER.info("Minecraft Transit Railway data successfully loaded for " + dimension);
	}

	public void tick() {
		lastMillis = currentMillis;
		currentMillis = System.currentTimeMillis();

		if (autoSave) {
			save(fileLoaderStations, true);
			save(fileLoaderPlatforms, true);
			save(fileLoaderSidings, true);
			save(fileLoaderRoutes, true);
			save(fileLoaderDepots, true);
			save(fileLoaderLifts, true);
			save(fileLoaderRailNodes, true);
			autoSave = false;
		}
	}

	public void save() {
		autoSave = true;
	}

	public void stop() {
		save(fileLoaderStations, false);
		save(fileLoaderPlatforms, false);
		save(fileLoaderSidings, false);
		save(fileLoaderRoutes, false);
		save(fileLoaderDepots, false);
		save(fileLoaderLifts, false);
		save(fileLoaderRailNodes, false);
	}

	public boolean matchMillis(long millis) {
		final long previousDifference = Utilities.circularDifference(millis, lastMillis, MILLIS_PER_DAY);
		final long currentDifference = Utilities.circularDifference(currentMillis, millis, MILLIS_PER_DAY);
		return previousDifference > 0 && previousDifference < MILLIS_PER_DAY / 2 && currentDifference < MILLIS_PER_DAY / 2;
	}

	private <T extends SerializedDataBase> void save(FileLoader<T> fileLoader, boolean useReducedHash) {
		final long autoSaveStartMillis = System.currentTimeMillis();
		final IntIntImmutablePair saveCounts = fileLoader.save(useReducedHash);
		Main.LOGGER.info(String.format("Save complete for %s in %s second(s)", dimension, (System.currentTimeMillis() - autoSaveStartMillis) / 1000F));
		if (saveCounts.leftInt() > 0) {
			Main.LOGGER.info("- Changed: " + saveCounts.leftInt());
		}
		if (saveCounts.rightInt() > 0) {
			Main.LOGGER.info("- Deleted: " + saveCounts.rightInt());
		}
	}
}
