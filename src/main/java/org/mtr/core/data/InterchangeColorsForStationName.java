package org.mtr.core.data;

import org.mtr.core.generated.data.InterchangeColorsForStationNameSchema;
import org.mtr.core.serializer.ReaderBase;

public class InterchangeColorsForStationName extends InterchangeColorsForStationNameSchema {

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

	public void addColor(InterchangeRouteNamesForColor interchangeRouteNamesForColor) {
		interchangeRouteNamesForColorList.add(interchangeRouteNamesForColor);
	}

	String getStationName() {
		return stationName;
	}

	@FunctionalInterface
	public interface ColorConsumer {
		void accept(int color, InterchangeRouteNamesForColor interchangeRouteNamesForColor);
	}
}
