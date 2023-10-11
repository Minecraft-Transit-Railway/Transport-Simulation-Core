import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {connectStations, drawLine, setColorByIndex} from "./drawing.js";
import Callback from "./callback.js";
import SETTINGS from "./settings.js";

const handlers = {setup, resize, draw, main};
const callback = new Callback(data => {
	if (data !== undefined) {
		postMessage("");
	}
	renderer.render(scene, camera);
});
const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
let renderer;
let scene;
let camera;

onmessage = ({data}) => {
	const handler = handlers[data.type];
	if (handler !== undefined) {
		handler(data);
	}
};

function setup(data) {
	const {canvas} = data;
	renderer = new THREE.WebGLRenderer({antialias: true, canvas});
	renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
	scene = new THREE.Scene();
	camera = new THREE.OrthographicCamera(0, 0, 0, 0, -20, 20);
}

function resize(data) {
	const {canvasWidth, canvasHeight, devicePixelRatio} = data;
	camera.aspect = canvasWidth / canvasHeight;
	camera.left = -canvasWidth / 2;
	camera.right = canvasWidth / 2;
	camera.top = canvasHeight / 2;
	camera.bottom = -canvasHeight / 2;
	camera.zoom = devicePixelRatio;
	camera.updateProjectionMatrix();
	renderer.setSize(canvasWidth, canvasHeight, false);
	draw(data);
}

function draw(data) {
	const {zoom, centerX, centerY, canvasWidth, canvasHeight, maxLineConnectionLength} = data;
	callback.update([zoom, centerX, centerY, canvasWidth, canvasHeight], maxLineConnectionLength);
}

function main(data) {
	const {
		stations,
		lineConnectionValues,
		maxLineConnectionLength,
		stationConnectionValues,
		backgroundColor,
		textColor,
		blackAndWhite,
		routeTypesSettings,
		interchangeStyle,
	} = data;
	scene.background = new THREE.Color(parseInt(backgroundColor, 16));
	scene.clear();
	callback.reset()

	stations.forEach(station => {
		const {x, z, rotate, width, height} = station;
		const newWidth = width * 3 * SETTINGS.scale;
		const newHeight = height * 3 * SETTINGS.scale;

		const createShape = radius => {
			const newRadius = radius * SETTINGS.scale;
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

		const geometry1 = new THREE.ShapeGeometry(createShape(7));
		const geometry2 = new THREE.ShapeGeometry(createShape(5));

		const configureGeometry = (geometry, offset, rotation, color) => {
			const count = geometry.getAttribute("position").count;
			const colorArray = new Uint8Array(count * 3);
			geometry.setAttribute("color", new THREE.BufferAttribute(colorArray, 3, true));
			geometry.translate(0, 0, offset);
			geometry.rotateZ(rotation);
			for (let i = 0; i < colorArray.length / 3; i++) {
				setColorByIndex(colorArray, color, i, blackAndWhite);
			}
		}

		configureGeometry(geometry1, -1, rotate ? Math.PI / 4 : 0, textColor);
		configureGeometry(geometry2, 0, rotate ? Math.PI / 4 : 0, backgroundColor);

		const blob = new THREE.Mesh(BufferGeometryUtils.mergeGeometries([geometry1, geometry2], false), materialWithVertexColors);
		scene.add(blob);

		callback.add(([zoom, centerX, centerY]) => {
			const canvasX = x * zoom + centerX;
			const canvasY = z * zoom + centerY;
			blob.position.x = canvasX;
			blob.position.y = -canvasY;
		});
	});

	const geometry = new THREE.BufferGeometry();
	const positionAttribute = new THREE.BufferAttribute(new Float32Array(SETTINGS.maxVertices), 3);
	const colorAttribute = new THREE.BufferAttribute(new Uint8Array(SETTINGS.maxVertices), 3, true);
	geometry.setAttribute("position", positionAttribute);
	geometry.setAttribute("color", colorAttribute);
	let tempIndex = 0;

	callback.add(() => tempIndex = 0);

	stationConnectionValues.forEach(stationConnection => {
		const {x1, z1, x2, z2} = stationConnection;

		callback.add(([zoom, centerX, centerY]) => {
			tempIndex = drawLine(positionAttribute, colorAttribute.array, interchangeStyle === 0 ? textColor : backgroundColor, blackAndWhite, tempIndex, x1 * zoom + centerX, z1 * zoom + centerY, x2 * zoom + centerX, z2 * zoom + centerY, interchangeStyle === 0 ? 1 : 0, 4);
			tempIndex = drawLine(positionAttribute, colorAttribute.array, interchangeStyle === 0 ? backgroundColor : textColor, blackAndWhite, tempIndex, x1 * zoom + centerX, z1 * zoom + centerY, x2 * zoom + centerX, z2 * zoom + centerY, interchangeStyle === 0 ? 2 : 1, 8);
		});
	});

	lineConnectionValues.forEach(lineConnection => {
		const {lineConnectionDetailsList, direction1, direction2, x1, z1, x2, z2, length} = lineConnection;

		for (let i = 0; i < lineConnectionDetailsList.length; i++) {
			const {color, offset1, offset2, oneWay} = lineConnectionDetailsList[i];
			const colorOffset = (i - lineConnectionDetailsList.length / 2 + 0.5) * 6 * SETTINGS.scale;
			const hollow = routeTypesSettings[color.split("|")[1]] === 2;

			callback.add(([zoom, centerX, centerY, canvasWidth, canvasHeight]) => {
				// z layers
				// 2-3     solid    two-way line
				// 4       solid    one-way arrows
				// 5-6     solid    one-way line
				// 7       hollow   white fill
				// 8-9     hollow   two-way line
				// 10      hollow   one-way arrows
				// 11      hollow   white fill
				// 12-13   hollow   one-way line

				tempIndex = connectStations(
					positionAttribute,
					colorAttribute.array,
					color,
					backgroundColor,
					textColor,
					blackAndWhite,
					tempIndex,
					x1 * zoom + centerX,
					z1 * zoom + centerY,
					x2 * zoom + centerX,
					z2 * zoom + centerY,
					direction1,
					direction2,
					offset1 * 6 * SETTINGS.scale,
					offset2 * 6 * SETTINGS.scale,
					colorOffset,
					(hollow ? (oneWay === 0 ? 8 : 12) : (oneWay === 0 ? 2 : 5)) + length / maxLineConnectionLength,
					hollow ? 10 : 4,
					(oneWay === 0 ? 7 : 11) + length / maxLineConnectionLength,
					canvasWidth,
					canvasHeight,
					oneWay,
					hollow,
				);
			});
		}
	});

	callback.add(() => {
		geometry.setDrawRange(0, Math.min(SETTINGS.maxVertices, tempIndex));
		positionAttribute.needsUpdate = true;
		colorAttribute.needsUpdate = true;
		geometry.computeBoundingSphere();
	});

	scene.add(new THREE.Mesh(geometry, materialWithVertexColors));
	resize(data);
}