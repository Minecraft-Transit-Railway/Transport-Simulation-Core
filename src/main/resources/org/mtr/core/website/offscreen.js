import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {configureGeometry, connectStations, setColor, setColorByIndex} from "./drawing.js";
import Callback from "./callback.js";
import SETTINGS from "./settings.js";

const handlers = {setup, resize, draw, main};
const callback = new Callback(() => renderer.render(scene, camera));
const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
let renderer;
let scene;
let camera;

onmessage = event => {
	const handler = handlers[event.data.type];
	if (handler !== undefined) {
		handler(event.data);
	}
};

function setup(data) {
	const {canvas} = data;
	renderer = new THREE.WebGLRenderer({antialias: true, canvas});
	renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
	scene = new THREE.Scene();
	camera = new THREE.OrthographicCamera(0, 0, 0, 0, -10, 10);
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
	const {zoom, centerX, centerY} = data;
	callback.update(zoom, centerX, centerY);
}

function main(data) {
	const {stations, connectionValues, connectionsCount, backgroundColor, textColor} = data;
	scene.background = new THREE.Color(parseInt(backgroundColor, 16));
	scene.clear();
	callback.reset();

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

		const geometry1 = new THREE.ShapeGeometry(createShape(11));
		const geometry2 = new THREE.ShapeGeometry(createShape(7));
		const geometry3 = new THREE.ShapeGeometry(createShape(5));
		setColor(configureGeometry(geometry1, -3, rotate ? Math.PI / 4 : 0), backgroundColor);
		setColor(configureGeometry(geometry2, -1, rotate ? Math.PI / 4 : 0), textColor);
		setColor(configureGeometry(geometry3, 0, rotate ? Math.PI / 4 : 0), backgroundColor);
		const blob = new THREE.Mesh(BufferGeometryUtils.mergeGeometries([geometry1, geometry2, geometry3], false), materialWithVertexColors);
		scene.add(blob);

		callback.add((zoom, centerX, centerY) => {
			const canvasX = x * zoom + centerX;
			const canvasY = z * zoom + centerY;
			blob.position.x = canvasX;
			blob.position.y = -canvasY;
		});
	});

	const geometry = new THREE.BufferGeometry();
	const count = 54;
	const positionAttribute = new THREE.BufferAttribute(new Float32Array(connectionsCount * count * 3), 3);
	geometry.setAttribute("position", positionAttribute);
	const colorAttribute = configureGeometry(geometry);
	let lineIndex = 0;

	connectionValues.forEach(connection => {
		const {colorsAndOffsets, direction1, direction2, x1, z1, x2, z2} = connection;

		for (let i = 0; i < colorsAndOffsets.length; i++) {
			const {color, offset1, offset2} = colorsAndOffsets[i];
			const lineArrayIndex = lineIndex * count;
			const colorOffset = (i - colorsAndOffsets.length / 2 + 0.5) * 6 * SETTINGS.scale;
			lineIndex++;

			for (let j = 0; j < count; j++) {
				setColorByIndex(colorAttribute, color, lineArrayIndex + j);
			}

			callback.add((zoom, centerX, centerY) => connectStations(
				positionAttribute,
				lineArrayIndex,
				count,
				x1 * zoom + centerX,
				z1 * zoom + centerY,
				x2 * zoom + centerX,
				z2 * zoom + centerY,
				direction1,
				direction2,
				offset1 * 6 * SETTINGS.scale,
				offset2 * 6 * SETTINGS.scale,
				colorOffset
			));
		}
	});

	callback.add(() => {
		positionAttribute.needsUpdate = true;
		geometry.computeBoundingSphere();
	});

	scene.add(new THREE.Mesh(geometry, materialWithVertexColors));
	resize(data);
}