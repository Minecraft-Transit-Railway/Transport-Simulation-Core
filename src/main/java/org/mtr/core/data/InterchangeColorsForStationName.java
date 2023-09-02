package org.mtr.core.data;

import org.mtr.core.generated.InterchangeColorsForStationNameSchema;
import org.mtr.core.serializers.ReaderBase;

public final class InterchangeColorsForStationName extends InterchangeColorsForStationNameSchema {

	public InterchangeColorsForStationName(String stationName) {
		super(stationName);
	}

	public InterchangeColorsForStationName(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void forEach(ColorConsumer consumer) {
		interchangeRouteNamesForColorList.forEach(interchangeRouteNamesForColor -> consumer.accept(interchangeRouteNamesForColor.getColor(), interchangeRouteNamesForColor));
	}

	String getStationName() {
		return stationName;
	}

	void addColor(InterchangeRouteNamesForColor interchangeRouteNamesForColor) {
		interchangeRouteNamesForColorList.add(interchangeRouteNamesForColor);
	}

	@FunctionalInterface
	public interface ColorConsumer {
		void accept(int color, InterchangeRouteNamesForColor interchangeRouteNamesForColor);
	}
}
