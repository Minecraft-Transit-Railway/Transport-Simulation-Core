export default class Callback {

	#draw;
	#callbacks = [];
	#dirty = false;

	constructor(draw) {
		this.#draw = draw;
	}

	reset() {
		this.#callbacks = [];
	}

	add(callback) {
		this.#callbacks.push(callback);
	}


	update(updateParameters, drawParameters) {
		if (!this.#dirty) {
			requestAnimationFrame(() => this.#onAnimationFrame(updateParameters, drawParameters));
		}
		this.#dirty = true;
	}

	#onAnimationFrame(updateParameters, drawParameters) {
		if (this.#dirty) {
			this.#callbacks.forEach(update => update(updateParameters));
			if (this.#draw !== undefined) {
				this.#draw(drawParameters);
			}
		}
		this.#dirty = false;
	}
}
