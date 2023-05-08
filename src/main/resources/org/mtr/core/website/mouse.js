const canvasElement = document.querySelector("#canvas");
let zoom = 1;
let centerX = 0;
let centerY = 0;
let isMouseDown = false;
let mouseCallback = () => {
};

document.addEventListener("wheel", event => {
	const {clientX, clientY, deltaY} = event;
	const zoomFactor = 0.999 ** deltaY;
	const x = clientX - canvasElement.clientWidth / 2 - centerX;
	const y = clientY - canvasElement.clientHeight / 2 - centerY;
	zoom *= zoomFactor;
	centerX += x - x * zoomFactor;
	centerY += y - y * zoomFactor;
	mouseCallback();
	event.preventDefault();
}, {passive: false});
document.addEventListener("mousemove", event => {
	if (isMouseDown) {
		const {movementX, movementY} = event;
		centerX += movementX;
		centerY += movementY;
		mouseCallback();
	}
});
document.addEventListener("mousedown", () => isMouseDown = true);
document.addEventListener("mouseup", () => isMouseDown = false);
document.addEventListener("mouseleave", () => isMouseDown = false);

export function setCenter(x, y) {
	centerX = x;
	centerY = y;
	zoom = 1;
}

export function setMouseCallback(callback) {
	mouseCallback = () => callback(zoom, centerX, centerY);
}

export function setResizeCallback(callback) {
	window.onresize = () => callback(zoom, centerX, centerY);
}
