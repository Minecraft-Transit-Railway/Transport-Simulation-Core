package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.generated.data.NameColorDataBaseSchema;
import org.mtr.core.generated.data.StationSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.tool.Utilities;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Common base for every domain entity that has a {@code (id, name, color)} identity tuple plus an
 * optional {@link TransportMode} — i.e. stations, platforms, sidings, routes, depots, lifts.
 *
 * <p>Implements {@link Comparable} so subclasses sort consistently across the codebase by
 * (case-insensitive) {@code name + color + id}, which is what the dashboard's data lists rely on.
 * Hex id formatting is cached lazily on first call.</p>
 */
public abstract class NameColorDataBase extends NameColorDataBaseSchema implements SerializedDataBaseWithId, Comparable<NameColorDataBase> {

	private String hexId;

	/** Construct a fresh entity bound to {@code transportMode} and {@code data}. */
	public NameColorDataBase(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	/** Deserialisation constructor used by the wire / on-disk layer. */
	public NameColorDataBase(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	/** @return the entity's id formatted as a fixed-width upper-case hex string (computed once and cached). */
	@Override
	public final String getHexId() {
		if (hexId == null) {
			hexId = Utilities.numberToPaddedHexString(id);
		}
		return hexId;
	}

	/** @return the underlying numeric id */
	public final long getId() {
		return id;
	}

	/** @return the entity's display name (may include MTR's {@code "name||subtitle"} marker — see {@link Utilities#formatName(String)}) */
	public final String getName() {
		return name;
	}

	/** @return the entity's RGB colour as a 24-bit packed integer */
	public final int getColor() {
		return (int) (color & 0xFFFFFF);
	}

	/** @return the entity's RGB colour as a 6-character upper-case hex string */
	public final String getColorHex() {
		return Utilities.numberToPaddedHexString(color, 6);
	}

	/** @return the {@link TransportMode} this entity belongs to (may be a placeholder for stations) */
	public final TransportMode getTransportMode() {
		return transportMode;
	}

	/** Replace the entity's display name. */
	public final void setName(String newName) {
		name = newName;
	}

	/** Replace the entity's RGB colour (truncated to 24 bits). */
	public final void setColor(int newColor) {
		color = newColor & 0xFFFFFF;
	}

	/**
	 * @return whether {@code data} is compatible with this entity's transport mode (stations are
	 * mode-agnostic and always match).
	 */
	public final boolean isTransportMode(NameColorDataBase data) {
		return noTransportMode() || data.noTransportMode() || data.transportMode == transportMode;
	}

	/** @see #isTransportMode(NameColorDataBase) */
	public final boolean isTransportMode(TransportMode transportMode) {
		return noTransportMode() || this.transportMode == transportMode;
	}

	private String combineNameColorId() {
		return (name + color + id).toLowerCase(Locale.ENGLISH);
	}

	private boolean noTransportMode() {
		return this instanceof StationSchema;
	}

	/**
	 * Filter {@code dataSet} to entities whose name contains {@code filter}, case-insensitive.
	 *
	 * @param <T>     the entity type
	 * @param dataSet entities to scan
	 * @param filter  case-insensitive substring; whitespace is trimmed
	 * @return matching entities in iteration order of {@code dataSet}
	 */
	public static <T extends NameColorDataBase> ObjectArrayList<T> getDataByName(ObjectArraySet<T> dataSet, String filter) {
		return dataSet.stream().filter(data -> data.getName().toLowerCase(Locale.ENGLISH).contains(filter.toLowerCase(Locale.ENGLISH).trim())).collect(Collectors.toCollection(ObjectArrayList::new));
	}

	@Override
	public int compareTo(NameColorDataBase compare) {
		return combineNameColorId().compareTo(compare.combineNameColorId());
	}
}
