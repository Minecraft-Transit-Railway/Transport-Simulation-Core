import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {getColorStyle} from "./utilities.js"
import {rotate, transform} from "./data.js"

const canvas = document.querySelector("#canvas");
const url = document.location.origin + document.location.pathname.replace("index.html", "");
const tan225 = Math.tan(Math.PI / 8);
const scale = 1;
const maxText = 0;

function setup() {
	const resize = () => {
		const width = canvas.clientWidth * devicePixelRatio;
		const height = canvas.clientHeight * devicePixelRatio;
		camera.aspect = width / height;
		camera.left = -width / 2;
		camera.right = width / 2;
		camera.top = height / 2;
		camera.bottom = -height / 2;
		camera.updateProjectionMatrix();
		renderer.setSize(width, height, false);
		draw();
	};
	const draw = () => renderer.render(scene, camera);

	const renderer = new THREE.WebGLRenderer({antialias: true, canvas});
	renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
	const scene = new THREE.Scene();
	scene.background = new THREE.Color(getColorStyle("backgroundColor"));
	const camera = new THREE.OrthographicCamera(0, 0, 0, 0, -10, 10);
	camera.zoom = devicePixelRatio;
	window.onresize = resize;
	return {scene, resize, draw};
}

function setColor(colors, color, startIndex = 0, count = Infinity) {
	const colorComponents = [(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF];
	for (let i = 0; i < Math.min(colors.length / 3, count); i++) {
		for (let j = 0; j < 3; j++) {
			colors[(startIndex + i) * 3 + j] = colorComponents[j];
		}
	}
}

function configureGeometry(geometry, offset = 0, rotation = 0) {
	const count = geometry.getAttribute("position").count;
	const colors = new Uint8Array(count * 3);
	geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3, true));
	if (offset !== 0) {
		geometry.translate(0, 0, offset);
	}
	if (rotation !== 0) {
		geometry.rotateZ(rotation);
	}
	return colors;
}

function main() {
	const {scene, resize, draw} = setup();
	const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});

	let canvasElements = [];
	let zoom = 1;
	let centerX = 0;
	let centerY = 0;
	let isMouseDown = false;

	fetch(url + "data").then(data => data.json()).then(data => {
		const labelElement = document.querySelector("#labels");
		const [sortedStations, connections] = transform(data[0]);
		canvasElements = [];
		scene.clear();

		let i = 0;
		sortedStations.forEach(station => {
			const {name, x, z, rotate, width, height} = station;
			const element = document.createElement("div");
			element.innerHTML = name.replace("|", "<br/>");
			labelElement.appendChild(element);

			const createShape = radius => {
				const newRadius = radius * scale;
				const newWidth = width * 3 * scale;
				const newHeight = height * 3 * scale;
				const shape = new THREE.Shape();
				const toRadians = angle => angle * Math.PI / 180;
				shape.moveTo(-newWidth, newHeight + newRadius);
				shape.arc(0, -newRadius, newRadius, toRadians(90), toRadians(180));
				shape.lineTo(-newWidth - newRadius, -newHeight);
				shape.arc(newRadius, 0, newRadius, toRadians(180), toRadians(270));
				shape.lineTo(newWidth, -newHeight - newRadius);
				shape.arc(0, newRadius, newRadius, toRadians(270), toRadians(360));
				shape.lineTo(newWidth + newRadius, newHeight);
				shape.arc(-newRadius, 0, newRadius, toRadians(0), toRadians(90));
				return shape;
			};

			const geometry1 = new THREE.ShapeGeometry(createShape(11));
			const geometry2 = new THREE.ShapeGeometry(createShape(7));
			const geometry3 = new THREE.ShapeGeometry(createShape(5));
			setColor(configureGeometry(geometry1, -3, rotate ? Math.PI / 4 : 0), getColorStyle("backgroundColor"));
			setColor(configureGeometry(geometry2, -1, rotate ? Math.PI / 4 : 0), getColorStyle("textColor"));
			setColor(configureGeometry(geometry3, 0, rotate ? Math.PI / 4 : 0), getColorStyle("backgroundColor"));
			const blob = new THREE.Mesh(BufferGeometryUtils.mergeGeometries([geometry1, geometry2, geometry3], false), materialWithVertexColors);

			const update = (renderedTextCount, renderCallback) => {
				const canvasX = x * zoom + centerX;
				const canvasY = z * zoom + centerY;
				blob.position.x = canvasX;
				blob.position.y = -canvasY;
				const halfCanvasWidth = canvas.clientWidth / 2;
				const halfCanvasHeight = canvas.clientHeight / 2;
				if (renderedTextCount < maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
					element.style.transform = `translate(-50%, 0) translate(${canvasX + halfCanvasWidth}px,${canvasY + halfCanvasHeight}px)`;
					element.style.display = "";
					renderCallback();
				} else {
					element.style.display = "none";
				}
			};

			update(i, () => i++);
			scene.add(blob);
			canvasElements.push(update);
		});

		const connectionValues = Object.values(connections);
		const geometry = new THREE.BufferGeometry();
		const count = 36;
		const positions = new THREE.BufferAttribute(new Float32Array(connectionValues.length * count * 3), 3);
		geometry.setAttribute("position", positions);
		const colors = configureGeometry(geometry);

		for (let i = 0; i < connectionValues.length; i++) {
			const {
				color,
				direction1,
				direction2,
				x1,
				z1,
				x2,
				z2,
				offsetX1,
				offsetY1,
				offsetX2,
				offsetY2,
			} = connectionValues[i];
			setColor(colors, color, i * count, count);

			const update = () => {
				const direction = (direction2 - direction1 + 4) % 4;
				const canvasX1 = x1 * zoom + centerX + offsetX1 * 6 * scale;
				const canvasY1 = z1 * zoom + centerY + offsetY1 * 6 * scale;
				const canvasX2 = x2 * zoom + centerX + offsetX2 * 6 * scale;
				const canvasY2 = z2 * zoom + centerY + offsetY2 * 6 * scale;
				const [x, y] = rotate(canvasX2 - canvasX1, canvasY2 - canvasY1, -direction1);
				const points = [[0, 0]];
				const extraX = Math.max(0, Math.abs(x) - Math.abs(y)) * Math.sign(x);
				const extraY = Math.max(0, Math.abs(y) - Math.abs(x)) * Math.sign(y);
				const halfExtraX = extraX / 2;
				const halfExtraY = extraY / 2;
				const halfX = x / 2;
				const halfY = y / 2;
				const sign1 = x > 0 === y > 0 ? -1 : 1;
				const sign2 = x > 0 === y < 0 ? -1 : 1;
				const routeIndex1 = rotate(offsetX1, offsetY1, -direction1)[0] * 6 * scale * tan225;
				const [routeIndex2X, routeIndex2Y] = rotate(offsetX2, offsetY2, -direction1).map(value => value * 6 * scale * tan225);
				const horizontal = Math.abs(x) > Math.abs(y);

				if (direction === 0) {
					points.push([0, halfExtraY + sign1 * routeIndex1]);
					points.push([halfX - halfExtraX, halfY + sign1 * routeIndex1]);
					points.push([halfX + halfExtraX, halfY + sign1 * routeIndex1]);
					points.push([x, y - halfExtraY + sign1 * routeIndex1]);
					points.push([x, y]);
				} else if (direction === 2) {
					points.push([0, extraY + (horizontal ? sign1 * routeIndex1 : routeIndex2Y)]);
					points.push([x - extraX + (horizontal ? routeIndex1 : sign1 * routeIndex2Y), y]);
					points.push([x, y]);
				} else if (!horizontal && (sign1 >= 0 && direction === 3 || sign1 < 0 && direction === 1)) {
					points.push([0, extraY]);
					points.push([x, y]);
				} else {
					points.push([0, 0]);
					points.push([0, 0]);
				}

				for (let j = 1; j < points.length; j++) {
					const [point1X, point1Y] = points[j - 1];
					const [point2X, point2Y] = points[j];
					const angle = Math.atan2(point2Y - point1Y, point2X - point1X);
					const endOffsetX1 = Math.cos(angle + Math.PI / 2) * 3 * scale;
					const endOffsetY1 = Math.sin(angle + Math.PI / 2) * 3 * scale;
					const endOffsetX2 = Math.cos(angle) * tan225 * 3 * scale;
					const endOffsetY2 = Math.sin(angle) * tan225 * 3 * scale;

					const [newPoint1X, newPoint1Y] = rotate(point1X + endOffsetX1 - endOffsetX2, point1Y + endOffsetY1 - endOffsetY2, direction1);
					const [newPoint2X, newPoint2Y] = rotate(point2X + endOffsetX1 + endOffsetX2, point2Y + endOffsetY1 + endOffsetY2, direction1);
					const [newPoint3X, newPoint3Y] = rotate(point2X - endOffsetX1 + endOffsetX2, point2Y - endOffsetY1 + endOffsetY2, direction1);
					const [newPoint4X, newPoint4Y] = rotate(point1X - endOffsetX1 - endOffsetX2, point1Y - endOffsetY1 - endOffsetY2, direction1);

					positions.setXYZ(count * i + j * 6 + 0, newPoint1X + canvasX1, -(newPoint1Y + canvasY1), -2);
					positions.setXYZ(count * i + j * 6 + 1, newPoint2X + canvasX1, -(newPoint2Y + canvasY1), -2);
					positions.setXYZ(count * i + j * 6 + 2, newPoint3X + canvasX1, -(newPoint3Y + canvasY1), -2);
					positions.setXYZ(count * i + j * 6 + 3, newPoint3X + canvasX1, -(newPoint3Y + canvasY1), -2);
					positions.setXYZ(count * i + j * 6 + 4, newPoint4X + canvasX1, -(newPoint4Y + canvasY1), -2);
					positions.setXYZ(count * i + j * 6 + 5, newPoint1X + canvasX1, -(newPoint1Y + canvasY1), -2);
				}
			};

			update();
			canvasElements.push(update);
		}

		canvasElements.push(() => {
			positions.needsUpdate = true;
			geometry.computeBoundingSphere();
		});
		scene.add(new THREE.Mesh(geometry, materialWithVertexColors));

		resize();
	});

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
}

main();
