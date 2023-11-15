package org.mtr.core.operation;

import org.mtr.core.generated.operation.ArrivalsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class ArrivalsResponse extends ArrivalsResponseSchema {

	public ArrivalsResponse() {
		super();
	}

	public ArrivalsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void iterateArrivals(ArrivalConsumer arrivalConsumer) {
		for (int i = 0; i < arrivals.size(); i++) {
			arrivalConsumer.apply(i, arrivals.get(i));
		}
	}

	public void add(ArrivalResponse arrivalResponse) {
		arrivals.add(arrivalResponse);
	}

	@FunctionalInterface
	public interface ArrivalConsumer {
		void apply(int arrivalIndex, ArrivalResponse arrivalResponse);
	}
}
