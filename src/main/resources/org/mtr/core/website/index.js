import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {getColorStyle, isCJK} from "./utilities.js"
import {transform} from "./data.js"
import {connectStations} from "./drawing.js";
import {
	addCanvasElement,
	getCoordinates,
	getXCoordinate,
	getYCoordinate,
	resetCanvasElements,
	setDrawFunction
} from "./mouse.js";

const canvas = document.querySelector("#canvas");
const url = `${document.location.origin}${document.location.pathname.replace("index.html", "")}mtr/api/data/`;
const scale = 1;
const maxText = 32;
const blackAndWhite = false;

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
	const r = (color >> 16) & 0xFF;
	const g = (color >> 8) & 0xFF;
	const b = color & 0xFF;
	const average = (r + g + b) / 3;
	const colorComponents = blackAndWhite ? [average, average, average] : [r, g, b];
	for (let i = 0; i < 3; i++) {
		colorAttribute[index * 3 + i] = colorComponents[i];
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

	fetch(url + "stations-and-routes").then(data => data.json()).then(data => {
		const labelElement = document.querySelector("#labels");
		const {stations} = data.data;
		const [connections, connectionsCount] = transform(data.data);
		resetCanvasElements();
		scene.clear();

		stations.forEach(station => {
			const {name, x, z, rotate, width, height} = station;
			if (width !== undefined || height !== undefined) {
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
			}
		});

		const connectionValues = Object.values(connections);
		const geometry = new THREE.BufferGeometry();
		const count = 54;
		const positionAttribute = new THREE.BufferAttribute(new Float32Array(connectionsCount * count * 3), 3);
		geometry.setAttribute("position", positionAttribute);
		const colorAttribute = configureGeometry(geometry);

		let lineIndex = 0;
		connectionValues.forEach(connectionValue => {
			const {
				colors,
				direction1,
				direction2,
				x1,
				z1,
				x2,
				z2,
				offsets1,
				offsets2,
			} = connectionValue;

			for (let i = 0; i < colors.length; i++) {
				const lineArrayIndex = lineIndex * count;
				const offset1 = offsets1[i] * 6 * scale;
				const offset2 = offsets2[i] * 6 * scale;
				const colorOffset = (i - colors.length / 2 + 0.5) * 6 * scale;

				for (let k = 0; k < count; k++) {
					setColorByIndex(colorAttribute, colors[i], lineArrayIndex + k);
				}

				const update = () => connectStations(
					positionAttribute,
					lineArrayIndex,
					getXCoordinate(x1),
					getYCoordinate(z1),
					getXCoordinate(x2),
					getYCoordinate(z2),
					direction1,
					direction2,
					offset1,
					offset2,
					colorOffset,
					scale
				);

				update();
				addCanvasElement(update);
				lineIndex++;
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
