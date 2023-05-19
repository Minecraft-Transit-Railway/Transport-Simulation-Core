package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public abstract class AreaBase<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> extends NameColorDataBase {

	private long cornerXMin;
	private long cornerZMin;
	private long cornerXMax;
	private long cornerZMax;
	public final ObjectAVLTreeSet<U> savedRails = new ObjectAVLTreeSet<>();

	private static final String KEY_X_MIN = "x_min";
	private static final String KEY_Z_MIN = "z_min";
	private static final String KEY_X_MAX = "x_max";
	private static final String KEY_Z_MAX = "z_max";

	public AreaBase(TransportMode transportMode, Simulator simulator) {
		super(transportMode, simulator);
	}

	public AreaBase(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);
		final long[] corners = {0, 0, 0, 0};
		readerBase.unpackLong(KEY_X_MIN, value -> corners[0] = value);
		readerBase.unpackLong(KEY_Z_MIN, value -> corners[1] = value);
		readerBase.unpackLong(KEY_X_MAX, value -> corners[2] = value);
		readerBase.unpackLong(KEY_Z_MAX, value -> corners[3] = value);
		setCorners(corners[0], corners[1], corners[2], corners[3]);
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeCorners(writerBase);
	}

	public void serializeCorners(WriterBase writerBase) {
		writerBase.writeLong(KEY_X_MIN, cornerXMin);
		writerBase.writeLong(KEY_Z_MIN, cornerZMin);
		writerBase.writeLong(KEY_X_MAX, cornerXMax);
		writerBase.writeLong(KEY_Z_MAX, cornerZMax);
	}

	public void setCorners(long cornerX1, long cornerZ1, long cornerX2, long cornerZ2) {
		cornerXMin = Math.min(cornerX1, cornerX2);
		cornerXMax = Math.max(cornerX1, cornerX2);
		cornerZMin = Math.min(cornerZ1, cornerZ2);
		cornerZMax = Math.max(cornerZ1, cornerZ2);
	}

	public boolean inArea(long x, long z) {
		return validCorners(this) && Utilities.isBetween(x, cornerXMin, cornerXMax) && Utilities.isBetween(z, cornerZMin, cornerZMax);
	}

	public boolean intersecting(AreaBase<T, U> areaBase) {
		return validCorners(this) && validCorners(areaBase) && (inThis(areaBase) || areaBase.inThis(this));
	}

	public Position getCenter() {
		return validCorners(this) ? new Position((cornerXMin + cornerXMax) / 2, 0, (cornerZMin + cornerZMax) / 2) : null;
	}

	private boolean inThis(AreaBase<T, U> areaBase) {
		return inArea(areaBase.cornerXMin, areaBase.cornerZMin) || inArea(areaBase.cornerXMin, areaBase.cornerZMax) || inArea(areaBase.cornerXMax, areaBase.cornerZMin) || inArea(areaBase.cornerXMax, areaBase.cornerZMax);
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> boolean validCorners(AreaBase<T, U> areaBase) {
		return areaBase != null && areaBase.cornerXMax > areaBase.cornerXMin && areaBase.cornerZMax > areaBase.cornerZMin;
	}
}
