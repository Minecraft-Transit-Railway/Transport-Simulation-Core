package org.mtr.core.data;

import org.mtr.core.generated.LiftSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Angle;

public final class Lift extends LiftSchema {

	public Lift(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Lift(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public double getHeight() {
		return height;
	}

	public double getWidth() {
		return width;
	}

	public double getDepth() {
		return depth;
	}

	public double getOffsetX() {
		return offsetX;
	}

	public double getOffsetY() {
		return offsetY;
	}

	public double getOffsetZ() {
		return offsetZ;
	}

	public boolean getIsDoubleSided() {
		return isDoubleSided;
	}

	public String getStyle() {
		return style;
	}

	public Angle getAngle() {
		return angle;
	}

	public double getSpeed() {
		return speed;
	}

	public boolean getReversed() {
		return reversed;
	}

	public void setHeight(double height) {
		this.height = height;
	}

	public void setWidth(double width) {
		this.width = width;
	}

	public void setDepth(double depth) {
		this.depth = depth;
	}

	public void setOffsetX(double offsetX) {
		this.offsetX = offsetX;
	}

	public void setOffsetY(double offsetY) {
		this.offsetY = offsetY;
	}

	public void setOffsetZ(double offsetZ) {
		this.offsetZ = offsetZ;
	}

	public void setIsDoubleSided(boolean isDoubleSided) {
		this.isDoubleSided = isDoubleSided;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public void setAngle(Angle angle) {
		this.angle = angle;
	}
}
