import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {atan45, getColorStyle, isCJK, rotate, trig45} from "./utilities.js"
import {transform} from "./data.js"
import {
	addCanvasElement,
	getCoordinates,
	getXCoordinate,
	getYCoordinate,
	resetCanvasElements,
	setDrawFunction
} from "./mouse.js";

const canvas = document.querySelector("#canvas");
const url = document.location.origin + document.location.pathname.replace("index.html", "");
const tan225 = Math.tan(Math.PI / 8);
const scale = 1;
const maxText = 32;

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
	setDrawFunction(draw);

	const renderer = new THREE.WebGLRenderer({antialias: true, canvas});
	renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
	const scene = new THREE.Scene();
	scene.background = new THREE.Color(getColorStyle("backgroundColor"));
	const camera = new THREE.OrthographicCamera(0, 0, 0, 0, -10, 10);
	camera.zoom = devicePixelRatio;
	window.onresize = resize;
	return {scene, resize};
}

function setColor(colorAttribute, color) {
	for (let i = 0; i < colorAttribute.length / 3; i++) {
		setColorByIndex(colorAttribute, color, i);
	}
}

function setColorByIndex(colorAttribute, color, index) {
	const colorComponents = [(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF];
	for (let i = 0; i < 3; i++) {
		colorAttribute[index * 3 + i] = colorComponents[i];
	}
}

function drawLine(positionAttribute, points, index, count, lineOffset, offsetX, offsetY, direction) {
	const newPoints = [];
	points.forEach(point => {
		const lastPoint = newPoints[newPoints.length - 1];
		let addPoint = true;
		if (lastPoint !== undefined) {
			const [x1, y1, z1] = lastPoint;
			const [x2, y2, z2] = point;
			if (x1 === x2 && y1 === y2 && z1 === z2) {
				addPoint = false;
			}
		}
		if (addPoint) {
			newPoints.push(point);
		} else {
			newPoints.pop();
		}
	});

	const angleChanges = [];

	for (let i = 0; i < newPoints.length; i++) {
		const point1 = newPoints[i - 1];
		const point2 = newPoints[i];
		const point3 = newPoints[i + 1];
		const [point2X, point2Y] = point2;
		let angleChange = 0;
		if (point1 !== undefined && point3 !== undefined) {
			const [point1X, point1Y] = point1;
			const [point3X, point3Y] = point3;
			const angle1 = atan45(point2Y - point1Y, point2X - point1X);
			const angle2 = atan45(point3Y - point2Y, point3X - point2X);
			angleChange = (angle2 - angle1 + 8) % 4;
		}
		angleChanges.push(angleChange === 3 ? -1 : angleChange);
	}

	for (let i = 1; i < newPoints.length; i++) {
		const [point1X, point1Y] = newPoints[i - 1];
		const [point2X, point2Y] = newPoints[i];
		const angleChange1 = angleChanges[i - 1];
		const angleChange2 = angleChanges[i];
		const angle = atan45(point2Y - point1Y, point2X - point1X);
		const [endOffsetX1, endOffsetY1] = trig45(angle + 2, 3 * scale);
		const [endOffsetX2, endOffsetY2] = trig45(angle, tan225 * 3 * scale);
		const lineOffsetX1 = (endOffsetX1 + endOffsetX2 * angleChange1) * 2 * lineOffset;
		const lineOffsetY1 = (endOffsetY1 + endOffsetY2 * angleChange1) * 2 * lineOffset;
		const lineOffsetX2 = (endOffsetX1 - endOffsetX2 * angleChange2) * 2 * lineOffset;
		const lineOffsetY2 = (endOffsetY1 - endOffsetY2 * angleChange2) * 2 * lineOffset;
		const [newPoint1X, newPoint1Y] = rotate(point1X + endOffsetX1 - endOffsetX2 + lineOffsetX1, point1Y + endOffsetY1 - endOffsetY2 + lineOffsetY1, direction);
		const [newPoint2X, newPoint2Y] = rotate(point2X + endOffsetX1 + endOffsetX2 + lineOffsetX2, point2Y + endOffsetY1 + endOffsetY2 + lineOffsetY2, direction);
		const [newPoint3X, newPoint3Y] = rotate(point2X - endOffsetX1 + endOffsetX2 + lineOffsetX2, point2Y - endOffsetY1 + endOffsetY2 + lineOffsetY2, direction);
		const [newPoint4X, newPoint4Y] = rotate(point1X - endOffsetX1 - endOffsetX2 + lineOffsetX1, point1Y - endOffsetY1 - endOffsetY2 + lineOffsetY1, direction);

		positionAttribute.setXYZ(index * count + i * 6 + 0, newPoint1X + offsetX, -(newPoint1Y + offsetY), -2);
		positionAttribute.setXYZ(index * count + i * 6 + 1, newPoint2X + offsetX, -(newPoint2Y + offsetY), -2);
		positionAttribute.setXYZ(index * count + i * 6 + 2, newPoint3X + offsetX, -(newPoint3Y + offsetY), -2);
		positionAttribute.setXYZ(index * count + i * 6 + 3, newPoint3X + offsetX, -(newPoint3Y + offsetY), -2);
		positionAttribute.setXYZ(index * count + i * 6 + 4, newPoint4X + offsetX, -(newPoint4Y + offsetY), -2);
		positionAttribute.setXYZ(index * count + i * 6 + 5, newPoint1X + offsetX, -(newPoint1Y + offsetY), -2);
	}

	for (let i = newPoints.length; i < count; i++) {
		for (let j = 0; j < 6; j++) {
			positionAttribute.setXYZ(index * count + i * 6 + j, 0, 0, 0);
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
	const {scene, resize} = setup();
	const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});

	fetch(url + "data").then(data => data.json()).then(data => {
		const labelElement = document.querySelector("#labels");
		const {stations} = data[0];
		const [connections, connectionsCount] = transform(data[0]);
		resetCanvasElements();
		scene.clear();

		stations.forEach(station => {
			const {name, x, z, rotate, width, height} = station;
			const newWidth = width * 3 * scale;
			const newHeight = height * 3 * scale;
			const textOffset = (rotate ? Math.max(newHeight, newWidth) * Math.SQRT1_2 : newHeight) + 9 * scale;
			const element = document.createElement("div");
			name.split("|").forEach(namePart => {
				const namePartElement = document.createElement("p");
				namePartElement.innerText = namePart;
				namePartElement.className = `station-name ${isCJK(namePart) ? "cjk" : ""}`;
				element.appendChild(namePartElement);
			});
			labelElement.appendChild(element);

			const createShape = radius => {
				const newRadius = radius * scale;
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
				const [canvasX, canvasY] = getCoordinates(x, z);
				blob.position.x = canvasX;
				blob.position.y = -canvasY;
				const halfCanvasWidth = canvas.clientWidth / 2;
				const halfCanvasHeight = canvas.clientHeight / 2;
				if (renderedTextCount < maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
					element.style.transform = `translate(-50%, 0) translate(${canvasX + halfCanvasWidth}px,${canvasY + halfCanvasHeight + textOffset}px)`;
					element.style.display = "";
					renderCallback();
				} else {
					element.style.display = "none";
				}
			};

			update(0, () => {
			});
			scene.add(blob);
			addCanvasElement(update);
		});

		const connectionValues = Object.values(connections);
		const geometry = new THREE.BufferGeometry();
		const count = 48;
		const positionAttribute = new THREE.BufferAttribute(new Float32Array(connectionsCount * count * 3), 3);
		geometry.setAttribute("position", positionAttribute);
		const colorAttribute = configureGeometry(geometry);

		let j2 = 0;
		connectionValues.forEach(connectionValue => {
			const {
				colors,
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
			} = connectionValue;
			for (let i = 0; i < colors.length; i++) {
				const j = j2;
				const colorOffset = i - (colors.length - 1) / 2;
				for (let k = 0; k < count; k++) {
					setColorByIndex(colorAttribute, colors[i], j * count + k);
				}

				const update = () => {
					const direction = (direction2 - direction1 + 4) % 4;
					const canvasX1 = getXCoordinate(x1) + offsetX1 * 6 * scale;
					const canvasY1 = getYCoordinate(z1) + offsetY1 * 6 * scale;
					const canvasX2 = getXCoordinate(x2) + offsetX2 * 6 * scale;
					const canvasY2 = getYCoordinate(z2) + offsetY2 * 6 * scale;
					const [x, y] = rotate(canvasX2 - canvasX1, canvasY2 - canvasY1, -direction1);
					const points = [[0, 0, -2]];
					const extraX = Math.max(0, Math.abs(x) - Math.abs(y)) * Math.sign(x);
					const extraY = Math.max(0, Math.abs(y) - Math.abs(x)) * Math.sign(y);
					const halfExtraX = extraX / 2;
					const halfExtraY = extraY / 2;
					const halfX = x / 2;
					const halfY = y / 2;
					const sign1 = x > 0 === y > 0 ? -1 : 1;
					const sign2 = x > 0 === y < 0 ? -1 : 1;
					const horizontal = Math.abs(x) > Math.abs(y);

					if (direction === 0) {
						points.push([0, halfExtraY, -4]);
						points.push([halfX - halfExtraX, halfY, -4]);
						points.push([halfX + halfExtraX, halfY, -4]);
						points.push([x, y - halfExtraY, -4]);
						points.push([x, y, -2]);
					} else if (direction === 2) {
						points.push([0, extraY]);
						points.push([x - extraX, y]);
						points.push([x, y, -2]);
					} else if (!horizontal && (sign1 >= 0 && direction === 3 || sign1 < 0 && direction === 1)) {
						points.push([0, extraY]);
						points.push([x, y, -2]);
					}

					drawLine(positionAttribute, points, j, count, colorOffset, canvasX1, canvasY1, direction1);
				};

				update();
				addCanvasElement(update);
				j2++;
			}
		});

		addCanvasElement(() => {
			positionAttribute.needsUpdate = true;
			geometry.computeBoundingSphere();
		});
		scene.add(new THREE.Mesh(geometry, materialWithVertexColors));

		resize();
	});
}

main();
