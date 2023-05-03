const canvas = document.querySelector("#canvas");
let canvasElements = [];
let zoom = 1;
let centerX = 0;
let centerY = 0;
let isMouseDown = false;
let draw = () => {
};

document.addEventListener("wheel", event => {
	const {clientX, clientY, deltaY} = event;
	const zoomFactor = 0.999 ** deltaY;
	const x = clientX - canvas.clientWidth / 2 - centerX;
	const y = clientY - canvas.clientHeight / 2 - centerY;
	zoom *= zoomFactor;
	centerX += x - x * zoomFactor;
	centerY += y - y * zoomFactor;
	let i = 0;
	canvasElements.forEach(update => update(i, () => i++));
	draw();
	event.preventDefault();
}, {passive: false});
document.addEventListener("mousemove", event => {
	if (isMouseDown) {
		const {movementX, movementY} = event;
		centerX += movementX;
		centerY += movementY;
		let i = 0;
		canvasElements.forEach(update => update(i, () => i++));
		draw();
	}
});
document.addEventListener("mousedown", () => isMouseDown = true);
document.addEventListener("mouseup", () => isMouseDown = false);
document.addEventListener("mouseleave", () => isMouseDown = false);

export function getCoordinates(x, y) {
	return [x * zoom + centerX, y * zoom + centerY];
}

export function getXCoordinate(x) {
	return x * zoom + centerX;
}

export function getYCoordinate(y) {
	return y * zoom + centerY;
}

export function addCanvasElement(update) {
	canvasElements.push(update);
}

export function resetCanvasElements() {
	canvasElements = [];
}

export function setDrawFunction(callback) {
	draw = callback;
}
