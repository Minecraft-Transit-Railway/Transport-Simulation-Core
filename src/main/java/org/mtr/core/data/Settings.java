package org.mtr.core.data;

import org.mtr.core.generated.data.SettingsSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

public class Settings extends SettingsSchema implements Utilities {

	public Settings(long lastSimulationMillis) {
		super(lastSimulationMillis);
	}

	public Settings(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return Utilities.numberToPaddedHexString(0);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public long getLastSimulationMillis() {
		final long currentMillis = System.currentTimeMillis();
		return lastSimulationMillis == 0 ? currentMillis : currentMillis - ((currentMillis - lastSimulationMillis) % MILLIS_PER_DAY);
	}
}
