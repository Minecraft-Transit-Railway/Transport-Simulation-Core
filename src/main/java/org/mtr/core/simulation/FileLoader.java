package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.reader.MessagePackHelper;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class FileLoader {

	private boolean useReducedHash = true;
	private int filesWritten;
	private int filesDeleted;
	private long autoSaveStartMillis;

	private final Simulator simulator;
	private final String dimension;

	private final List<Long> dirtyStationIds = new ArrayList<>();
	private final List<Long> dirtyPlatformIds = new ArrayList<>();
	private final List<Long> dirtySidingIds = new ArrayList<>();
	private final List<Long> dirtyRouteIds = new ArrayList<>();
	private final List<Long> dirtyDepotIds = new ArrayList<>();
	private final List<Long> dirtyLiftIds = new ArrayList<>();
	private final List<Position> dirtyRailPositions = new ArrayList<>();
	private final List<SignalBlocks.SignalBlock> dirtySignalBlocks = new ArrayList<>();

	private final Map<Path, Integer> existingFiles = new HashMap<>();
	private final List<Path> checkFilesToDelete = new ArrayList<>();

	private final Path stationsPath;
	private final Path platformsPath;
	private final Path sidingsPath;
	private final Path routesPath;
	private final Path depotsPath;
	private final Path liftsPath;
	private final Path railsPath;
	private final Path signalBlocksPath;

	public FileLoader(Simulator simulator, String dimension, Path savePath) {
		this.simulator = simulator;
		this.dimension = dimension;

		stationsPath = savePath.resolve("stations");
		platformsPath = savePath.resolve("platforms");
		sidingsPath = savePath.resolve("sidings");
		routesPath = savePath.resolve("routes");
		depotsPath = savePath.resolve("depots");
		liftsPath = savePath.resolve("lifts");
		railsPath = savePath.resolve("rails");
		signalBlocksPath = savePath.resolve("signal-blocks");

		try {
			Files.createDirectories(stationsPath);
			Files.createDirectories(platformsPath);
			Files.createDirectories(sidingsPath);
			Files.createDirectories(routesPath);
			Files.createDirectories(depotsPath);
			Files.createDirectories(liftsPath);
			Files.createDirectories(railsPath);
			Files.createDirectories(signalBlocksPath);
		} catch (IOException e) {
			e.printStackTrace();
		}

		existingFiles.clear();
		readMessagePackFromFile(stationsPath, Station::new, simulator.stations::add, false);
		readMessagePackFromFile(platformsPath, Platform::new, simulator.platforms::add, true);
		readMessagePackFromFile(sidingsPath, Siding::new, simulator.sidings::add, true);
		readMessagePackFromFile(routesPath, Route::new, simulator.routes::add, false);
		readMessagePackFromFile(depotsPath, messagePackHelper -> new Depot(messagePackHelper, simulator), simulator.depots::add, false);
		readMessagePackFromFile(liftsPath, Lift::new, simulator.lifts::add, true);
		readMessagePackFromFile(railsPath, RailEntry::new, railEntry -> simulator.rails.put(railEntry.position, railEntry.connections), true);
		readMessagePackFromFile(signalBlocksPath, SignalBlocks.SignalBlock::new, simulator.signalBlocks.signalBlocks::add, true);

		simulator.dataCache.sync();
		simulator.stations.forEach(Station::init);
		simulator.platforms.forEach(Platform::init);
		simulator.sidings.forEach(Siding::init);
		simulator.routes.forEach(Route::init);
		simulator.depots.forEach(Depot::init);
		simulator.lifts.forEach(Lift::init);

		Main.LOGGER.info("Minecraft Transit Railway data successfully loaded for " + dimension);
	}

	public void fullSave() {
		useReducedHash = false;
		dirtyStationIds.clear();
		dirtyPlatformIds.clear();
		dirtySidingIds.clear();
		dirtyRouteIds.clear();
		dirtyDepotIds.clear();
		dirtyLiftIds.clear();
		dirtyRailPositions.clear();
		dirtySignalBlocks.clear();
		checkFilesToDelete.clear();
		autoSave();
		while (true) {
			if (autoSaveTick()) {
				Main.LOGGER.info("Minecraft Transit Railway data successfully saved for " + dimension);
				break;
			}
		}
	}

	public void autoSave() {
		if (checkFilesToDelete.isEmpty()) {
			autoSaveStartMillis = System.currentTimeMillis();
			filesWritten = 0;
			filesDeleted = 0;
			dirtyStationIds.addAll(simulator.dataCache.stationIdMap.keySet());
			dirtyPlatformIds.addAll(simulator.dataCache.platformIdMap.keySet());
			dirtySidingIds.addAll(simulator.dataCache.sidingIdMap.keySet());
			dirtyRouteIds.addAll(simulator.dataCache.routeIdMap.keySet());
			dirtyDepotIds.addAll(simulator.dataCache.depotIdMap.keySet());
			dirtyLiftIds.addAll(simulator.dataCache.liftsIdMap.keySet());
			dirtyRailPositions.addAll(simulator.rails.keySet());
			dirtySignalBlocks.addAll(simulator.signalBlocks.signalBlocks);
			checkFilesToDelete.addAll(existingFiles.keySet());
		}
	}

	public boolean autoSaveTick() {
		final boolean deleteEmptyOld = checkFilesToDelete.isEmpty();

		boolean hasSpareTime = writeDirtyDataToFile(dirtyStationIds, id -> simulator.dataCache.stationIdMap.get(id.longValue()), id -> id, stationsPath);
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtyPlatformIds, id -> simulator.dataCache.platformIdMap.get(id.longValue()), id -> id, platformsPath);
		}
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtySidingIds, id -> simulator.dataCache.sidingIdMap.get(id.longValue()), id -> id, sidingsPath);
		}
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtyRouteIds, id -> simulator.dataCache.routeIdMap.get(id.longValue()), id -> id, routesPath);
		}
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtyDepotIds, id -> simulator.dataCache.depotIdMap.get(id.longValue()), id -> id, depotsPath);
		}
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtyLiftIds, id -> simulator.dataCache.liftsIdMap.get(id.longValue()), id -> id, liftsPath);
		}
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtyRailPositions, position -> simulator.rails.containsKey(position) ? new RailEntry(position, simulator.rails.get(position)) : null, position -> position.x, railsPath);
		}
		if (hasSpareTime) {
			hasSpareTime = writeDirtyDataToFile(dirtySignalBlocks, signalBlock -> signalBlock, signalBlock -> signalBlock.id, signalBlocksPath);
		}

		final boolean doneWriting = dirtyStationIds.isEmpty() && dirtyPlatformIds.isEmpty() && dirtySidingIds.isEmpty() && dirtyRouteIds.isEmpty() && dirtyDepotIds.isEmpty() && dirtyLiftIds.isEmpty() && dirtyRailPositions.isEmpty() && dirtySignalBlocks.isEmpty();
		if (hasSpareTime && !checkFilesToDelete.isEmpty() && doneWriting) {
			final Path path = checkFilesToDelete.remove(0);
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
			existingFiles.remove(path);
			filesDeleted++;
		}

		if (!deleteEmptyOld && checkFilesToDelete.isEmpty()) {
			if (!useReducedHash || filesWritten > 0 || filesDeleted > 0) {
				Main.LOGGER.info("Save complete for " + dimension + " in " + (System.currentTimeMillis() - autoSaveStartMillis) / 1000 + " second(s)");
				if (filesWritten > 0) {
					Main.LOGGER.info("- Changed: " + filesWritten);
				}
				if (filesDeleted > 0) {
					Main.LOGGER.info("- Deleted: " + filesDeleted);
				}
			}
		}

		return doneWriting && checkFilesToDelete.isEmpty();
	}

	private <T extends SerializedDataBase> void readMessagePackFromFile(Path path, Function<MessagePackHelper, T> getData, Consumer<T> callback, boolean skipVerify) {
		try (final Stream<Path> pathStream = Files.list(path)) {
			pathStream.forEach(idFolder -> {
				try (final Stream<Path> folderStream = Files.list(idFolder)) {
					folderStream.forEach(idFile -> {
						try (final InputStream inputStream = Files.newInputStream(idFile)) {
							try (final MessageUnpacker messageUnpacker = MessagePack.newDefaultUnpacker(inputStream)) {
								final int size = messageUnpacker.unpackMapHeader();
								final HashMap<String, Value> result = new HashMap<>(size);

								for (int i = 0; i < size; i++) {
									result.put(messageUnpacker.unpackString(), messageUnpacker.unpackValue());
								}

								final T data = getData.apply(new MessagePackHelper(result));
								if (skipVerify || !(data instanceof NameColorDataBase) || !((NameColorDataBase) data).name.isEmpty()) {
									callback.accept(data);
								}

								existingFiles.put(idFile, getHash(data, true));
							} catch (IOException e) {
								e.printStackTrace();
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Path writeMessagePackToFile(SerializedDataBase data, long id, Path path) {
		final Path parentPath = path.resolve(String.valueOf(id % 100));
		try {
			Files.createDirectories(parentPath);
			final Path dataPath = parentPath.resolve(String.valueOf(id));
			final int hash = getHash(data, useReducedHash);

			if (!existingFiles.containsKey(dataPath) || hash != existingFiles.get(dataPath)) {
				final MessagePacker messagePacker = MessagePack.newDefaultPacker(Files.newOutputStream(dataPath, StandardOpenOption.CREATE));
				messagePacker.packMapHeader(data.messagePackLength());
				data.toMessagePack(messagePacker);
				messagePacker.close();

				existingFiles.put(dataPath, hash);
				filesWritten++;
			}

			return dataPath;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private <T extends SerializedDataBase, U> boolean writeDirtyDataToFile(List<U> dirtyData, Function<U, T> getId, Function<U, Long> idToLong, Path path) {
		final long millis = System.currentTimeMillis();
		while (!dirtyData.isEmpty()) {
			final U id = dirtyData.remove(0);
			final T data = getId.apply(id);
			if (data != null) {
				final Path newPath = writeMessagePackToFile(data, idToLong.apply(id), path);
				if (newPath != null) {
					checkFilesToDelete.remove(newPath);
				}
			}
			if (System.currentTimeMillis() - millis >= 2) {
				return false;
			}
		}
		return true;
	}

	private static int getHash(SerializedDataBase data, boolean useReducedHash) {
		try {
			final MessageBufferPacker messageBufferPacker = MessagePack.newDefaultBufferPacker();

			if (useReducedHash) {
				messageBufferPacker.packMapHeader(data.messagePackLength());
				data.toMessagePack(messageBufferPacker);
			} else {
				messageBufferPacker.packMapHeader(data.fullMessagePackLength());
				data.toFullMessagePack(messageBufferPacker);
			}

			final int hash = Arrays.hashCode(messageBufferPacker.toByteArray());
			messageBufferPacker.close();

			return hash;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	private static class RailEntry extends SerializedDataBase {

		public final Position position;
		public final Object2ObjectOpenHashMap<Position, Rail> connections;

		private static final String KEY_NODE_POSITION_X = "node_position_x";
		private static final String KEY_NODE_POSITION_Y = "node_position_y";
		private static final String KEY_NODE_POSITION_Z = "node_position_z";
		private static final String KEY_RAIL_CONNECTIONS = "rail_connections";

		public RailEntry(Position position, Object2ObjectOpenHashMap<Position, Rail> connections) {
			this.position = position;
			this.connections = connections;
		}

		public <T extends ReaderBase<U, T>, U> RailEntry(T readerBase) {
			position = getPosition(readerBase);
			connections = new Object2ObjectOpenHashMap<>();
		}

		@Override
		public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
			if (readerBase instanceof MessagePackHelper) {
				readerBase.iterateReaderArray(KEY_RAIL_CONNECTIONS, value -> connections.put(getPosition((MessagePackHelper) value), new Rail((MessagePackHelper) value)));
			}
		}

		@Override
		public void toMessagePack(MessagePacker messagePacker) throws IOException {
			messagePacker.packString(KEY_NODE_POSITION_X).packLong(position.x);
			messagePacker.packString(KEY_NODE_POSITION_Y).packLong(position.y);
			messagePacker.packString(KEY_NODE_POSITION_Z).packLong(position.z);

			messagePacker.packString(KEY_RAIL_CONNECTIONS).packArrayHeader(connections.size());
			for (final Map.Entry<Position, Rail> entry : connections.entrySet()) {
				final Position endNodePosition = entry.getKey();
				messagePacker.packMapHeader(entry.getValue().messagePackLength() + 3);
				messagePacker.packString(KEY_NODE_POSITION_X).packLong(endNodePosition.x);
				messagePacker.packString(KEY_NODE_POSITION_Y).packLong(endNodePosition.y);
				messagePacker.packString(KEY_NODE_POSITION_Z).packLong(endNodePosition.z);
				entry.getValue().toMessagePack(messagePacker);
			}
		}

		@Override
		public int messagePackLength() {
			return 4;
		}

		private static <T extends ReaderBase<U, T>, U> Position getPosition(T readerBase) {
			final long x = readerBase.getLong(KEY_NODE_POSITION_X, 0);
			final long y = readerBase.getLong(KEY_NODE_POSITION_Y, 0);
			final long z = readerBase.getLong(KEY_NODE_POSITION_Z, 0);
			final Position[] newPosition = {new Position(x, y, z)};
			DataFixer.unpackRailEntry(readerBase, value -> newPosition[0] = value);
			return newPosition[0];
		}
	}
}
