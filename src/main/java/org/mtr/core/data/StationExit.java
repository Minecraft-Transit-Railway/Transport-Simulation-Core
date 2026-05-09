package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.data.StationExitSchema;
import org.mtr.core.serializer.ReaderBase;

/**
 * One labelled exit out of a {@link Station} &mdash; the "Exit A1, Hennessy Road" entries
 * shown to passengers for wayfinding.
 *
 * <p>Each exit carries a short identifier ({@link #getName()}) and an ordered list of
 * destination strings ({@link #getDestinations()}); ordering is delegated to
 * {@link #compareTo(StationExit)} which sorts by the (typically alphanumeric) name.</p>
 */
public final class StationExit extends StationExitSchema implements Comparable<StationExit> {

	public StationExit() {
		super();
	}

	public StationExit(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public String getName() {
		return name;
	}

	public ObjectArrayList<String> getDestinations() {
		return destinations;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int compareTo(StationExit stationExit) {
		if (equals(stationExit)) {
			return 0;
		} else {
			return getName().compareTo(stationExit.getName());
		}
	}
}
