import * as THREE from "../../utility/three.module.min.js";
import * as BufferGeometryUtils from "../../utility/BufferGeometryUtils";
import {connectStations, drawLine, setColorByIndex} from "../../utility/drawing";
import {Callback} from "../../utility/callback";
import SETTINGS from "../../utility/settings";
import {LineConnection} from "../../data/lineConnection";
import {StationConnection} from "../../data/stationConnection";
import {StationWithPosition} from "../../service/data.service";

const handlers: { setup: (data: SetupData) => void, resize: (data: ResizeData) => void, draw: (data: DrawData) => void, main: (data: MainData) => void } = {setup, resize, draw, main};
const callback = new Callback<[number, number, number, number, number], number>(drawParameters => {
	if (drawParameters !== undefined) {
		postMessage("");
	}
	renderer.render(scene, camera);
});
const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
const blackColor = 0x000000;
const whiteColor = 0xFFFFFF;
let renderer: THREE.WebGLRenderer;
let scene: THREE.Scene;
let camera: THREE.OrthographicCamera;

onmessage = (event: MessageEvent<Message>) => {
	const handler = handlers[event.data.type] as (data: SetupData | DrawData) => void;
	if (handler !== undefined) {
		handler(event.data.content);
	}
};

function setup(data: SetupData) {
	const {canvas} = data;
	renderer = new THREE.WebGLRenderer({antialias: true, canvas});
	renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
	scene = new THREE.Scene();
	camera = new THREE.OrthographicCamera(0, 0, 0, 0, -20, 20);
}

function resize(data: ResizeData) {
	const {canvasWidth, canvasHeight, devicePixelRatio} = data;
	(camera as any).aspect = canvasWidth / canvasHeight;
	camera.left = -canvasWidth / 2;
	camera.right = canvasWidth / 2;
	camera.top = canvasHeight / 2;
	camera.bottom = -canvasHeight / 2;
	camera.zoom = devicePixelRatio;
	camera.updateProjectionMatrix();
	renderer.setSize(canvasWidth, canvasHeight, false);
	draw(data);
}

function draw(data: DrawData) {
	const {zoom, centerX, centerY, canvasWidth, canvasHeight, maxLineConnectionLength} = data;
	callback.update([zoom, centerX, centerY, canvasWidth, canvasHeight], maxLineConnectionLength);
}

function main(data: MainData) {
	const {
		stations,
		lineConnections,
		maxLineConnectionLength,
		stationConnections,
		backgroundColor,
		darkMode,
		routeTypesSettings,
		interchangeStyle,
	} = data;
	scene.background = new THREE.Color(backgroundColor).convertLinearToSRGB();
	scene.clear();
	callback.reset();

	stations.forEach(station => {
		const {x, z, rotate, width, height} = station;
		const newWidth = width * 3 * SETTINGS.scale;
		const newHeight = height * 3 * SETTINGS.scale;

		const createShape = (radius: number) => {
			const newRadius = radius * SETTINGS.scale;
			const shape = new THREE.Shape();
			const toRadians = (angle: number) => angle * Math.PI / 180;
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

		const configureGeometry = (geometry: THREE.ShapeGeometry, offset: number, rotation: number, color: number) => {
			const count = geometry.getAttribute("position").count;
			const colorArray = new Uint8Array(count * 3);
			geometry.setAttribute("color", new THREE.BufferAttribute(colorArray, 3, true));
			geometry.translate(0, 0, offset);
			geometry.rotateZ(rotation);
			for (let i = 0; i < colorArray.length / 3; i++) {
				setColorByIndex(colorArray, color, i);
			}
		}

		configureGeometry(geometry1, -1, rotate ? Math.PI / 4 : 0, darkMode ? whiteColor : blackColor);
		configureGeometry(geometry2, 0, rotate ? Math.PI / 4 : 0, darkMode ? blackColor : whiteColor);

		const blob = new THREE.Mesh(BufferGeometryUtils.mergeGeometries([geometry1, geometry2], false), materialWithVertexColors);
		scene.add(blob);

		callback.add(([zoom, centerX, centerY]) => {
			const canvasX = x * zoom + centerX;
			const canvasY = z * zoom + centerY;
			(blob as any).position.x = canvasX;
			(blob as any).position.y = -canvasY;
		});
	});

	const geometry = new THREE.BufferGeometry();
	const positionAttribute = new THREE.BufferAttribute(new Float32Array(SETTINGS.maxVertices), 3);
	const colorAttribute = new THREE.BufferAttribute(new Uint8Array(SETTINGS.maxVertices), 3, true);
	geometry.setAttribute("position", positionAttribute);
	geometry.setAttribute("color", colorAttribute);
	let tempIndex = 0;

	callback.add(() => tempIndex = 0);

	stationConnections.forEach(stationConnection => {
		const {x1, z1, x2, z2} = stationConnection;

		callback.add(([zoom, centerX, centerY]) => {
			tempIndex = drawLine(positionAttribute, colorAttribute.array, interchangeStyle === 0 === darkMode ? whiteColor : blackColor, tempIndex, x1 * zoom + centerX, z1 * zoom + centerY, x2 * zoom + centerX, z2 * zoom + centerY, interchangeStyle === 0 ? 1 : 0, 4);
			tempIndex = drawLine(positionAttribute, colorAttribute.array, interchangeStyle === 0 ? backgroundColor : darkMode ? whiteColor : blackColor, tempIndex, x1 * zoom + centerX, z1 * zoom + centerY, x2 * zoom + centerX, z2 * zoom + centerY, interchangeStyle === 0 ? 2 : 1, 8);
		});
	});

	lineConnections.forEach(lineConnection => {
		const {lineConnectionParts, direction1, direction2, x1, z1, x2, z2, length} = lineConnection;

		for (let i = 0; i < lineConnectionParts.length; i++) {
			const {color, offset1, offset2, oneWay} = lineConnectionParts[i];
			const colorOffset = (i - lineConnectionParts.length / 2 + 0.5) * 6 * SETTINGS.scale;
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
					parseInt(color, 16),
					backgroundColor,
					darkMode ? whiteColor : blackColor,
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

export class Message {
	constructor(readonly type: "setup" | "resize" | "draw" | "main", readonly content: SetupData | DrawData) {
	}
}

export class SetupData {
	constructor(readonly canvas: OffscreenCanvas) {
	}
}

export class DrawData {
	constructor(readonly zoom: number, readonly centerX: number, readonly centerY: number, readonly canvasWidth: number, readonly canvasHeight: number, readonly mouseStationId: string, readonly mouseRouteId: string, readonly maxLineConnectionLength: number) {
	}
}

export class ResizeData extends DrawData {
	constructor(zoom: number, centerX: number, centerY: number, canvasWidth: number, canvasHeight: number, mouseStationId: string, mouseRouteId: string, maxLineConnectionLength: number, readonly devicePixelRatio: number) {
		super(zoom, centerX, centerY, canvasWidth, canvasHeight, mouseStationId, mouseRouteId, maxLineConnectionLength);
	}
}

export class MainData extends ResizeData {
	constructor(
		zoom: number, centerX: number, centerY: number, canvasWidth: number, canvasHeight: number, mouseStationId: string, mouseRouteId: string, maxLineConnectionLength: number, devicePixelRatio: number,
		readonly stations: StationWithPosition[],
		readonly lineConnections: LineConnection[],
		readonly stationConnections: StationConnection[],
		readonly backgroundColor: number,
		readonly darkMode: boolean,
		readonly routeTypesSettings: { [key: string]: number },
		readonly interchangeStyle: number
	) {
		super(zoom, centerX, centerY, canvasWidth, canvasHeight, mouseStationId, mouseRouteId, maxLineConnectionLength, devicePixelRatio);
	}
}
