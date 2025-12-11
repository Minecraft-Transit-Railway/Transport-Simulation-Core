package org.mtr.core.data;

import org.mtr.core.generated.data.AreaBaseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;

public abstract class SimpleAreaBase extends AreaBaseSchema {

	public SimpleAreaBase(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	public SimpleAreaBase(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	@Override
	protected final Position getDefaultPosition1() {
		return new Position(0, 0, 0);
	}

	@Override
	protected final Position getDefaultPosition2() {
		return new Position(0, 0, 0);
	}

	@Override
	public boolean isValid() {
		return !name.isEmpty();
	}

	public long getMinX() {
		return Math.min(position1.getX(), position2.getX());
	}

	public long getMaxX() {
		return Math.max(position1.getX(), position2.getX());
	}

	public long getMinY() {
		return Math.min(position1.getY(), position2.getY());
	}

	public long getMaxY() {
		return Math.max(position1.getY(), position2.getY());
	}

	public long getMinZ() {
		return Math.min(position1.getZ(), position2.getZ());
	}

	public long getMaxZ() {
		return Math.max(position1.getZ(), position2.getZ());
	}

	public void setCorners(Position position1, Position position2) {
		this.position1 = position1;
		this.position2 = position2;
	}

	public boolean inArea(Position position) {
		return inArea(position, 0);
	}

	public boolean inArea(Position position, double padding) {
		return validCorners(this) && Utilities.isBetween(position, position1, position2, padding);
	}

	public boolean intersecting(SimpleAreaBase simpleAreaBase) {
		return validCorners(this) && validCorners(simpleAreaBase) && (inThis(simpleAreaBase) || simpleAreaBase.inThis(this));
	}

	public Position getCenter() {
		return validCorners(this) ? new Position((position1.getX() + position2.getX()) / 2, (position1.getY() + position2.getY()) / 2, (position1.getZ() + position2.getZ()) / 2) : null;
	}

	private boolean inThis(SimpleAreaBase simpleAreaBase) {
		final long x1 = simpleAreaBase.position1.getX();
		final long y1 = simpleAreaBase.position1.getY();
		final long z1 = simpleAreaBase.position1.getZ();
		final long x2 = simpleAreaBase.position2.getX();
		final long y2 = simpleAreaBase.position2.getY();
		final long z2 = simpleAreaBase.position2.getZ();
		return inArea(simpleAreaBase.position1) || inArea(simpleAreaBase.position2) || inArea(new Position(x1, y1, z2)) || inArea(new Position(x1, y2, z1)) || inArea(new Position(x1, y2, z2)) || inArea(new Position(x2, y1, z1)) || inArea(new Position(x2, y1, z2)) || inArea(new Position(x2, y2, z1));
	}

	public static boolean validCorners(@Nullable SimpleAreaBase simpleAreaBase) {
		return simpleAreaBase != null && simpleAreaBase.position1 != null && simpleAreaBase.position2 != null;
	}
}
