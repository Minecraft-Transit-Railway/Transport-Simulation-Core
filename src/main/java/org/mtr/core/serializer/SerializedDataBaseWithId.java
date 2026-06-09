package org.mtr.core.serializer;

/**
 * Refinement of {@link SerializedDataBase} for entities that have a stable, hex-formatted id.
 *
 * <p>Used as the bound on {@link org.mtr.core.simulation.FileLoader} so the file-per-id
 * persistence layer can name files after {@link #getHexId()} and skip writing entities that
 * report {@link #isValid()} {@code == false}.</p>
 */
public interface SerializedDataBaseWithId extends SerializedDataBase {

	/**
	 * @return the entity's id formatted as a fixed-width upper-case hex string
	 */
	String getHexId();

	/**
	 * @return whether this entity should be persisted; entities returning {@code false} are
	 * dropped from the data set on the next save.
	 */
	boolean isValid();
}
