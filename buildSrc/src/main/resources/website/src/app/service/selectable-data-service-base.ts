import {DataServiceBase} from "./data-service-base";
import {Observable} from "rxjs";
import {DimensionService} from "./dimension.service";
import {EventEmitter} from "@angular/core";

export abstract class SelectableDataServiceBase<T, U> extends DataServiceBase<T> {
	public readonly selectionChanged = new EventEmitter<void>();
	private selectedData?: U;

	public readonly getSelectedData = () => this.selectedData;
	public readonly select = (dataKey: string) => {
		this.reset();
		this.selectedData = this.convert(dataKey);
		this.selectionChanged.emit();
	};
	public readonly clear = () => {
		this.reset();
		this.selectedData = undefined;
		this.selectionChanged.emit();
	};

	protected constructor(private readonly convert: (dataKey: string) => U | undefined, private readonly reset: () => void, sendData: (selectedData: U) => Observable<T> | void, processData: (data: T) => void, refreshInterval: number, dimensionService: DimensionService) {
		super(() => {
			if (this.selectedData) {
				return sendData(this.selectedData);
			}
		}, processData, refreshInterval, dimensionService);
	}
}
