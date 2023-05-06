const canvasElement = document.querySelector("#canvas");
let canvasCallbacks = [];
let zoom = 1;
let centerX = 0;
let centerY = 0;
let isMouseDown = false;
let dirty = false;
let draw = () => {
};

document.addEventListener("wheel", event => {
	const {clientX, clientY, deltaY} = event;
	const zoomFactor = 0.999 ** deltaY;
	const x = clientX - canvasElement.clientWidth / 2 - centerX;
	const y = clientY - canvasElement.clientHeight / 2 - centerY;
	zoom *= zoomFactor;
	centerX += x - x * zoomFactor;
	centerY += y - y * zoomFactor;
	queueAnimationFrame();
	event.preventDefault();
}, {passive: false});
document.addEventListener("mousemove", event => {
	if (isMouseDown) {
		const {movementX, movementY} = event;
		centerX += movementX;
		centerY += movementY;
		queueAnimationFrame();
	}
});
document.addEventListener("mousedown", () => isMouseDown = true);
document.addEventListener("mouseup", () => isMouseDown = false);
document.addEventListener("mouseleave", () => isMouseDown = false);

export function setCenter(x, y) {
	centerX = -x;
	centerY = -y;
	zoom = 1;
}

export function getCoordinates(x, y) {
	return [x * zoom + centerX, y * zoom + centerY];
}

export function getXCoordinate(x) {
	return x * zoom + centerX;
}

export function getYCoordinate(y) {
	return y * zoom + centerY;
}

export function addCanvasCallback(update) {
	canvasCallbacks.push(update);
}

export function resetCanvasObjects() {
	canvasCallbacks = [];
}

export function setDrawFunction(callback) {
	draw = callback;
}

function queueAnimationFrame() {
	if (!dirty) {
		requestAnimationFrame(onAnimationFrame);
	}
	dirty = true;
}

function onAnimationFrame() {
	if (dirty) {
		canvasCallbacks.forEach(update => update());
		draw();
	}
	dirty = false;
}
