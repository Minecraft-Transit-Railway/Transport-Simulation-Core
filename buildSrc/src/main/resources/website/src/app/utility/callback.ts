export class Callback<T, U> {

	private callbacks: ((updateParameters: T) => void)[] = [];
	private dirty = false;

	constructor(private readonly draw?: (drawParameters?: U) => void) {
	}

	public reset() {
		this.callbacks = [];
	}

	public add(callback: (updateParameters: T) => void) {
		this.callbacks.push(callback);
	}

	public update(updateParameters: T, drawParameters?: U) {
		if (!this.dirty) {
			requestAnimationFrame(() => this.onAnimationFrame(updateParameters, drawParameters));
		}
		this.dirty = true;
	}

	private onAnimationFrame(updateParameters: T, drawParameters?: U) {
		if (this.dirty) {
			this.callbacks.forEach(update => update(updateParameters));
			if (this.draw !== undefined) {
				this.draw(drawParameters);
			}
		}
		this.dirty = false;
	}
}
