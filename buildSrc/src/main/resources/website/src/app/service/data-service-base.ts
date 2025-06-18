import {catchError, EMPTY, Observable} from "rxjs";
import {DimensionService} from "./dimension.service";
import {EventEmitter} from "@angular/core";

export abstract class DataServiceBase<T> {
	public readonly dataProcessed = new EventEmitter<void>();
	private loading = false;
	private id = "";
	private timeoutId = 0;

	public readonly isLoading = () => this.loading;
	protected readonly getUrl = (endpoint: string) => `${document.location.origin}${document.location.pathname}mtr/api/map/${endpoint}?dimension=${this.dimensionService.getDimensionIndex()}`;
	protected readonly fetchData = (id: string) => {
		this.loading = true;
		this.id = id;
		clearTimeout(this.timeoutId);
		this.getDataInternal();
	};

	protected constructor(private readonly sendData: () => Observable<T> | void, private readonly processData: (data: T) => void, private readonly refreshInterval: number, protected readonly dimensionService: DimensionService) {
	}

	private getDataInternal() {
		const observable = this.sendData();
		if (observable) {
			const currentId = this.formatId();
			observable.pipe(catchError(error => {
				if (currentId === this.formatId()) {
					console.error(error);
					this.scheduleData();
				} else {
					console.log("skipped");
				}
				return EMPTY;
			})).subscribe(data => {
				if (currentId === this.formatId()) {
					this.loading = false;
					this.processData(data);
					this.dataProcessed.emit();
					this.scheduleData();
				} else {
					console.log("skipped");
				}
			});
		}
	}

	private scheduleData() {
		clearTimeout(this.timeoutId);
		this.timeoutId = setTimeout(() => this.getDataInternal(), this.refreshInterval) as unknown as number;
	}

	private formatId() {
		return `id_${this.id}`;
	}
}
