package org.mtr.legacy.data;

import org.mtr.core.data.Position;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.generated.data.SignalBlockSchema;

import java.util.UUID;

public final class LegacySignalBlock extends SignalBlockSchema {

	public LegacySignalBlock(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return Utilities.numberToPaddedHexString(color);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public int getColor() {
		return Color.values()[(int) color].color;
	}

	public boolean isRail(Position position1, Position position2) {
		return rails.stream().anyMatch(railUuid -> {
			final UUID uuid = UUID.fromString(railUuid);
			final long position1Long = DataFixer.asLong(position1);
			final long position2Long = DataFixer.asLong(position2);
			return position1Long == uuid.getLeastSignificantBits() && position2Long == uuid.getMostSignificantBits() || position2Long == uuid.getLeastSignificantBits() && position1Long == uuid.getMostSignificantBits();
		});
	}

	private enum Color {
		WHITE(16777215),
		ORANGE(14188339),
		MAGENTA(11685080),
		LIGHT_BLUE(6724056),
		YELLOW(15066419),
		LIME(8375321),
		PINK(15892389),
		GRAY(5000268),
		LIGHT_GRAY(10066329),
		CYAN(5013401),
		PURPLE(8339378),
		BLUE(3361970),
		BROWN(6704179),
		GREEN(6717235),
		RED(10040115),
		BLACK(1644825);

		private final int color;

		Color(int color) {
			this.color = color;
		}
	}
}
