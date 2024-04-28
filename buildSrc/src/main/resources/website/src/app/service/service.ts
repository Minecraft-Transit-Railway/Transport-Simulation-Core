import {catchError, EMPTY, Observable} from "rxjs";

export abstract class ServiceBase<T> {
	private loading = false;
	private id = "";
	private timeoutId = 0;

	protected constructor(private readonly sendData: () => Observable<T> | void, private readonly refreshInterval: number) {
	}

	protected abstract processData(data: T): void;

	protected getData(id: string) {
		this.loading = true;
		this.id = id;
		clearTimeout(this.timeoutId);
		this.getDataInternal();
	}

	public isLoading() {
		return this.loading;
	}

	private getDataInternal() {
		const observable = this.sendData();
		if (observable) {
			const currentId = this.formatId();
			const instance = this;
			observable.pipe(catchError(error => {
				if (currentId == instance.formatId()) {
					console.error(error);
					instance.scheduleData();
				} else {
					console.log("skipped");
				}
				return EMPTY;
			})).subscribe(data => {
				if (currentId == this.formatId()) {
					this.loading = false;
					this.processData(data);
					this.scheduleData();
				} else {
					console.log("skipped");
				}
			});
		}
	}

	private scheduleData() {
		clearTimeout(this.timeoutId);
		const instance = this;
		this.timeoutId = setTimeout(() => instance.getDataInternal(), this.refreshInterval);
	}

	private formatId() {
		return `id_${this.id}`;
	}
}
