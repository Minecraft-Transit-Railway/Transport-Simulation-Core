import {DataServiceBase} from "./data-service-base";
import {Observable, Subject} from "rxjs";
import {DimensionService} from "./dimension.service";
import {signal} from "@angular/core";

export abstract class SelectableDataServiceBase<T, U> extends DataServiceBase<T> {
	public readonly selectionChanged = new Subject<void>();
	public selectedData = signal<U | undefined>(undefined);

	public readonly select = (dataKey: string) => {
		this.reset();
		this.selectedData.set(this.convert(dataKey));
		this.selectionChanged.next();
	};
	public readonly clear = () => {
		this.reset();
		this.selectedData.set(undefined);
		this.selectionChanged.next();
	};

	protected constructor(private readonly convert: (dataKey: string) => U | undefined, private readonly reset: () => void, sendData: (selectedData: U) => Observable<T> | void, processData: (data: T) => void, refreshInterval: number, dimensionService: DimensionService) {
		super(() => {
			const selectedData = this.selectedData();
			if (selectedData) {
				return sendData(selectedData);
			}
		}, processData, refreshInterval, dimensionService);
	}
}
