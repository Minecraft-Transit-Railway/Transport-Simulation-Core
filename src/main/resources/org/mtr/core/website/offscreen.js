import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {configureGeometry, connectStations, setColor, setColorByIndex} from "./drawing.js";
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
	const {zoom, centerX, centerY, maxConnectionLength} = data;
	callback.update([zoom, centerX, centerY], maxConnectionLength);
}

function main(data) {
	const {stations, connectionValues, maxConnectionLength, backgroundColor, textColor} = data;
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
		setColor(configureGeometry(geometry1, -7, rotate ? Math.PI / 4 : 0), backgroundColor);
		setColor(configureGeometry(geometry2, -1, rotate ? Math.PI / 4 : 0), textColor);
		setColor(configureGeometry(geometry3, 0, rotate ? Math.PI / 4 : 0), backgroundColor);
		const blob = new THREE.Mesh(BufferGeometryUtils.mergeGeometries([geometry1, geometry2, geometry3], false), materialWithVertexColors);
		scene.add(blob);

		callback.add(([zoom, centerX, centerY]) => {
			const canvasX = x * zoom + centerX;
			const canvasY = z * zoom + centerY;
			blob.position.x = canvasX;
			blob.position.y = -canvasY;
		});
	});

	const geometryLines = new THREE.BufferGeometry();
	const geometryArrows = new THREE.BufferGeometry();
	let positionArrayLines = [];
	let colorArrayLines = [];
	let positionArrayArrows = [];
	let colorArrayArrows = [];

	callback.add(() => {
		positionArrayLines = [];
		colorArrayLines = [];
		positionArrayArrows = [];
		colorArrayArrows = [];
	});

	connectionValues.forEach(connection => {
		const {colorsAndOffsets, direction1, direction2, x1, z1, x2, z2, length} = connection;

		for (let i = 0; i < colorsAndOffsets.length; i++) {
			const {color, offset1, offset2, oneWay} = colorsAndOffsets[i];
			const colorOffset = (i - colorsAndOffsets.length / 2 + 0.5) * 6 * SETTINGS.scale;

			callback.add(([zoom, centerX, centerY]) => {
				const tempPositionArrayLines = [];
				const tempPositionArrayArrows = [];

				const pushPositionsAndColor = (tempPositionArray, positionArray, z, colorArray, newColor) => {
					for (let j = 0; j < tempPositionArray.length; j++) {
						tempPositionArray[j].push(z);
						positionArray.push(tempPositionArray[j]);
						colorArray.push(newColor);
					}
				};

				connectStations(
					tempPositionArrayLines,
					tempPositionArrayArrows,
					x1 * zoom + centerX,
					z1 * zoom + centerY,
					x2 * zoom + centerX,
					z2 * zoom + centerY,
					direction1,
					direction2,
					offset1 * 6 * SETTINGS.scale,
					offset2 * 6 * SETTINGS.scale,
					colorOffset,
					oneWay,
				);

				pushPositionsAndColor(tempPositionArrayLines, positionArrayLines, (oneWay === 0 ? 3 : 5) + length / maxConnectionLength, colorArrayLines, color);
				pushPositionsAndColor(tempPositionArrayArrows, positionArrayArrows, 4, colorArrayArrows, backgroundColor);
			});
		}
	});

	callback.add(() => {
		const setPositionAttribute = (geometry, positionArray, colorArray) => {
			const positionAttribute = new THREE.BufferAttribute(new Float32Array(positionArray.length * 3), 3);
			for (let i = 0; i < positionArray.length; i++) {
				positionAttribute.setXYZ(i, positionArray[i][0], -positionArray[i][1], -positionArray[i][2]);
			}

			const colorAttributeArray = new Uint8Array(colorArray.length * 3);
			for (let i = 0; i < colorArray.length; i++) {
				setColorByIndex(colorAttributeArray, colorArray[i], i);
			}

			geometry.setAttribute("position", positionAttribute);
			geometry.setAttribute("color", new THREE.BufferAttribute(colorAttributeArray, 3, true));
			geometry.computeBoundingSphere();
		};

		setPositionAttribute(geometryLines, positionArrayLines, colorArrayLines);
		setPositionAttribute(geometryArrows, positionArrayArrows, colorArrayArrows);
	});

	scene.add(new THREE.Mesh(geometryLines, materialWithVertexColors));
	scene.add(new THREE.Mesh(geometryArrows, materialWithVertexColors));
	resize(data);
}