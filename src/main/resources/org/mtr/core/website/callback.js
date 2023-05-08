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


	update(a, b, c) {
		if (!this.#dirty) {
			requestAnimationFrame(() => this.#onAnimationFrame(a, b, c));
		}
		this.#dirty = true;
	}

	#onAnimationFrame(a, b, c) {
		if (this.#dirty) {
			this.#callbacks.forEach(update => update(a, b, c));
			if (this.#draw !== undefined) {
				this.#draw();
			}
		}
		this.#dirty = false;
	}
}
