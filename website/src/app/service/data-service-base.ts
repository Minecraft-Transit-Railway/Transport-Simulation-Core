import {Observable} from "rxjs";
import {DimensionService} from "./dimension.service";
import {EventEmitter, signal} from "@angular/core";

export abstract class DataServiceBase<T> {
	public readonly dataProcessed = new EventEmitter<void>();
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
					if (currentId == this.formatId()) {
						this.loading.set(false);
						this.processData(data);
						this.dataProcessed.emit();
						this.scheduleData();
					} else {
						console.log("skipped");
					}
				},
				error: error => {
					if (currentId == this.formatId()) {
						console.error(error);
						this.scheduleData();
					} else {
						console.log("skipped");
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
