package org.mtr.core.serializer;

public interface SerializedDataBaseWithId extends SerializedDataBase {

	String getHexId();

	boolean isValid();
}
