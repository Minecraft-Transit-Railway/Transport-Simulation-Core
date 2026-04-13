import {Observable, Subject} from "rxjs";
import {DimensionService} from "./dimension.service";
import {signal} from "@angular/core";

export abstract class DataServiceBase<T> {
	public readonly dataProcessed = new Subject<void>();
	public readonly loading = signal(false);
	private id = "";
	private timeoutId = 0;

	protected readonly getUrl = (endpoint: string) => `${document.location.origin}${document.location.pathname}mtr/api/map/${endpoint}?dimension=${this.dimensionService.getDimensionIndex()}`;
	protected readonly fetchData = (id: string) => {
		this.loading.set(true);
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
			observable.subscribe({
				next: data => {
					if (currentId === this.formatId()) {
						this.loading.set(false);
						this.processData(data);
						this.dataProcessed.next();
						this.scheduleData();
					}
				},
				error: error => {
					if (currentId === this.formatId()) {
						console.error(error);
						this.scheduleData();
					}
				},
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
