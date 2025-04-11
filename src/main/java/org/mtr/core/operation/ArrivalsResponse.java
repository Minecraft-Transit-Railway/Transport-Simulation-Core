package org.mtr.core.operation;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.generated.operation.ArrivalsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class ArrivalsResponse extends ArrivalsResponseSchema {

	public ArrivalsResponse(long currentTime) {
		super(currentTime);
	}

	public ArrivalsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public long getCurrentTime() {
		return currentTime;
	}

	public ObjectImmutableList<ArrivalResponse> getArrivals() {
		return new ObjectImmutableList<>(arrivals);
	}

	public void add(ArrivalResponse arrivalResponse) {
		arrivals.add(arrivalResponse);
	}

	@FunctionalInterface
	public interface ArrivalConsumer {
		void apply(int arrivalIndex, ArrivalResponse arrivalResponse);
	}
}
