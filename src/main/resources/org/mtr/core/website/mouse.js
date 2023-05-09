import {getColorStyle} from "./utilities.js";

const canvasElement = document.querySelector("#canvas");
const loadingElement = document.querySelector("#loading");
const animateSteps = 50;
let zoom = 1;
let centerX = 0;
let centerY = 0;
let animateTarget;
let isMouseDown = false;
let enabled = false;
let firstDraw = true;
let touchPoints = [];
let mouseCallback = () => {
};

document.addEventListener("wheel", event => {
	const {clientX, clientY, deltaY} = event;
	zoomCanvas(clientX, clientY, deltaY);
	event.preventDefault();
}, {passive: false});

document.addEventListener("mousemove", event => {
	if (isMouseDown) {
		const {movementX, movementY} = event;
		moveCanvas(movementX, movementY);
	}
	event.preventDefault();
}, {passive: false});

document.addEventListener("touchmove", event => {
	const touchCount = touchPoints.length;
	const {touches} = event;
	if (touches.length === touchCount) {
		let movementX = 0;
		let movementY = 0;
		for (let i = 0; i < touchCount; i++) {
			movementX += touches.item(i).clientX - touchPoints[i][0];
			movementY += touches.item(i).clientY - touchPoints[i][1];
		}
		moveCanvas(movementX / touchCount, movementY / touchCount);
		if (touchCount >= 2) {
			const pinchX = Math.abs(touchPoints[1][0] - touchPoints[0][0]) - Math.abs(touches.item(1).clientX - touches.item(0).clientX);
			const pinchY = Math.abs(touchPoints[1][1] - touchPoints[0][1]) - Math.abs(touches.item(1).clientY - touches.item(0).clientY);
			zoomCanvas((touchPoints[0][0] + touchPoints[1][0]) / 2, (touchPoints[0][1] + touchPoints[1][1]) / 2, (pinchX + pinchY) * 3);
		}
	}
	setTouchPoints(event);
	event.preventDefault();
}, {passive: false});

document.addEventListener("mousedown", () => {
	isMouseDown = enabled;
	animateTarget = undefined;
});

document.addEventListener("mouseup", () => {
	isMouseDown = false;
	animateTarget = undefined;
});

document.addEventListener("mouseleave", () => {
	isMouseDown = false;
	animateTarget = undefined;
});

document.addEventListener("touchstart", event => {
	setTouchPoints(event);
	animateTarget = undefined;
});

document.addEventListener("touchend", () => {
	touchPoints = [];
	animateTarget = undefined;
});

function zoomCanvas(clientX, clientY, deltaY) {
	if (enabled) {
		const zoomFactor = 0.999 ** deltaY;
		const x = clientX - canvasElement.clientWidth / 2 - centerX;
		const y = clientY - canvasElement.clientHeight / 2 - centerY;
		zoom *= zoomFactor;
		centerX += x - x * zoomFactor;
		centerY += y - y * zoomFactor;
		mouseCallback();
	}
	animateTarget = undefined;
}

function moveCanvas(movementX, movementY) {
	if (enabled) {
		centerX += movementX;
		centerY += movementY;
		mouseCallback();
	}
	animateTarget = undefined;
}

function setTouchPoints(event) {
	touchPoints = [];
	for (let i = 0; i < event.touches.length; i++) {
		const {clientX, clientY} = event.touches.item(i);
		touchPoints.push([clientX, clientY]);
	}
}

export function setCenterOnFirstDraw(x, y) {
	if (firstDraw) {
		centerX = x;
		centerY = y;
		zoom = 1;
	}
	firstDraw = false;
}

export function animateCenter(x, y) {
	animateTarget = [x - centerX, y - centerY, 1 - zoom, 0];
	const animate = () => {
		if (animateTarget !== undefined) {
			const [changeX, changeY, changeZoom, progress] = animateTarget;
			const scaledProgress = Math.sin(progress) * Math.PI / animateSteps / 2;
			const incrementX = changeX * scaledProgress;
			const incrementY = changeY * scaledProgress;
			const incrementZoom = changeZoom * scaledProgress;
			if (progress >= Math.PI) {
				centerX = x;
				centerY = y;
				zoom = 1;
				animateTarget = undefined;
			} else {
				centerX += incrementX;
				centerY += incrementY;
				zoom += incrementZoom;
				animateTarget[3] += Math.PI / animateSteps;
				requestAnimationFrame(animate);
			}
			mouseCallback();
		}
	};
	animate();
}

export function getCurrentWindowValues() {
	return [zoom, centerX, centerY];
}

export function setMouseCallback(callback) {
	mouseCallback = () => callback(zoom, centerX, centerY);
}

export function setResizeCallback(callback) {
	window.onresize = () => callback(zoom, centerX, centerY);
}

export function setLoading(loading) {
	enabled = !loading;
	loadingElement.style.opacity = loading ? "1" : "0";
	loadingElement.style.backgroundColor = getColorStyle("backgroundColorTransparent");
}
