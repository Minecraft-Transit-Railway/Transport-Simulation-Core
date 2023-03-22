package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.mtr.core.Main;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.SerializedDataBase;
import org.mtr.core.reader.MessagePackHelper;

import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class FileLoader<T extends SerializedDataBase> {

	private final Set<T> data;
	private final Path path;
	private final Object2IntAVLTreeMap<String> fileHashes = new Object2IntAVLTreeMap<>();

	public FileLoader(Set<T> data, Function<MessagePackHelper, T> getData, Path path, boolean skipVerifyNameIsNotEmpty) {
		this.data = data;
		this.path = path;
		createDirectory(path);
		readMessagePackFromFile(getData, data::add, skipVerifyNameIsNotEmpty);
	}

	public IntIntImmutablePair save(boolean useReducedHash) {
		final ObjectArrayList<T> dirtyData = new ObjectArrayList<>(data);
		final ObjectArrayList<ObjectArrayList<String>> checkFilesToDelete = new ObjectArrayList<>();

		for (int i = 0; i <= 0xFF; i++) {
			checkFilesToDelete.add(new ObjectArrayList<>());
		}
		fileHashes.keySet().forEach(fileName -> checkFilesToDelete.get(getParentInt(fileName)).add(fileName));

		final int filesWritten = writeDirtyDataToFile(checkFilesToDelete, dirtyData, SerializedDataBase::getHexId, useReducedHash);
		int filesDeleted = 0;

		for (final ObjectArrayList<String> checkFilesToDeleteForParent : checkFilesToDelete) {
			for (final String fileName : checkFilesToDeleteForParent) {
				final Path filePath = path.resolve(getParent(fileName));
				try {
					Files.deleteIfExists(filePath);
					filesDeleted++;
				} catch (Exception e) {
					e.printStackTrace();
				}
				fileHashes.removeInt(filePath);
			}
		}

		return new IntIntImmutablePair(filesWritten, filesDeleted);
	}

	private void readMessagePackFromFile(Function<MessagePackHelper, T> getData, Consumer<T> callback, boolean skipVerifyNameIsNotEmpty) {
		try (final Stream<Path> pathStream = Files.list(path)) {
			pathStream.forEach(idFolder -> {
				try (final Stream<Path> folderStream = Files.list(idFolder)) {
					folderStream.forEach(idFile -> {
						try (final InputStream inputStream = Files.newInputStream(idFile)) {
							try (final MessageUnpacker messageUnpacker = MessagePack.newDefaultUnpacker(inputStream)) {
								final int size = messageUnpacker.unpackMapHeader();
								final Object2ObjectArrayMap<String, Value> result = new Object2ObjectArrayMap<>(size);

								for (int i = 0; i < size; i++) {
									result.put(messageUnpacker.unpackString(), messageUnpacker.unpackValue());
								}

								final T data = getData.apply(new MessagePackHelper(result));
								if (skipVerifyNameIsNotEmpty || !(data instanceof NameColorDataBase) || !((NameColorDataBase) data).name.isEmpty()) {
									callback.accept(data);
								}

								fileHashes.put(idFile.getFileName().toString(), getHash(data, true));
							} catch (Exception e) {
								e.printStackTrace();
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}

				try {
					Files.deleteIfExists(idFolder);
					Main.LOGGER.info(String.format("Deleted empty folder: %s", idFolder));
				} catch (DirectoryNotEmptyException ignored) {
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private int writeDirtyDataToFile(ObjectArrayList<ObjectArrayList<String>> checkFilesToDelete, ObjectArrayList<T> dirtyData, Function<T, String> getFileName, boolean useReducedHash) {
		int filesWritten = 0;
		while (!dirtyData.isEmpty()) {
			final T data = dirtyData.remove(0);

			if (data != null) {
				final String fileName = getFileName.apply(data);
				final Path parentPath = path.resolve(getParent(fileName));
				final int hash = getHash(data, useReducedHash);

				if (!fileHashes.containsKey(fileName) || hash != fileHashes.getInt(fileName)) {
					createDirectory(parentPath);

					try (final MessagePacker messagePacker = MessagePack.newDefaultPacker(Files.newOutputStream(parentPath.resolve(fileName), StandardOpenOption.CREATE))) {
						messagePacker.packMapHeader(data.messagePackLength());
						data.toMessagePack(messagePacker);
					} catch (Exception e) {
						e.printStackTrace();
					}

					fileHashes.put(fileName, hash);
					filesWritten++;
				}

				checkFilesToDelete.get(getParentInt(fileName)).remove(fileName);
			}
		}

		return filesWritten;
	}

	private static String getParent(String fileName) {
		return fileName.substring(Math.max(0, fileName.length() - 2));
	}

	private static int getParentInt(String fileName) {
		try {
			return Integer.parseInt(getParent(fileName), 16);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
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

	private static void createDirectory(Path path) {
		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
