import * as THREE from "./three.module.min.js";
import * as BufferGeometryUtils from "./BufferGeometryUtils.js";
import {getColorStyle, getRouteTypeIcon, isCJK} from "./utilities.js"
import {getData} from "./data.js"
import {configureGeometry, connectStations, setColor, setColorByIndex} from "./drawing.js";
import {
	addCanvasCallback,
	getCoordinates,
	getXCoordinate,
	getYCoordinate,
	resetCanvasObjects,
	setDrawFunction
} from "./mouse.js";
import SETTINGS from "./settings.js";

const canvasElement = document.querySelector("#canvas");
const labelElement = document.querySelector("#labels");

function setup() {
	const resize = () => {
		const width = canvasElement.clientWidth * devicePixelRatio;
		const height = canvasElement.clientHeight * devicePixelRatio;
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
	const renderer = new THREE.WebGLRenderer({antialias: true, canvas: canvasElement});
	renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
	const scene = new THREE.Scene();
	scene.background = new THREE.Color(getColorStyle("backgroundColor", true));
	const camera = new THREE.OrthographicCamera(0, 0, 0, 0, -10, 10);
	camera.zoom = devicePixelRatio;
	window.onresize = resize;
	return {scene, resize};
}

function main() {
	const {scene, resize} = setup();
	const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
	let renderedTextCount = 0;

	getData(() => {
		resetCanvasObjects();
		addCanvasCallback(() => renderedTextCount = 0);
		scene.clear();
		labelElement.innerHTML = "";
	}, station => {
		const {name, types, x, z, rotate, width, height} = station;
		const newWidth = width * 3 * SETTINGS.scale;
		const newHeight = height * 3 * SETTINGS.scale;
		const textOffset = (rotate ? Math.max(newHeight, newWidth) * Math.SQRT1_2 : newHeight) + 9 * SETTINGS.scale;
		const element = document.createElement("div");
		name.split("|").forEach(namePart => {
			const namePartElement = document.createElement("p");
			namePartElement.innerText = namePart;
			namePartElement.className = `station-name ${isCJK(namePart) ? "cjk" : ""}`;
			element.appendChild(namePartElement);
		});
		const routeTypesElement = document.createElement("p");
		routeTypesElement.className = "station-name material-symbols-outlined";
		routeTypesElement.innerText = types.map(getRouteTypeIcon).join("");
		element.appendChild(routeTypesElement);
		labelElement.appendChild(element);

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
		setColor(configureGeometry(geometry1, -3, rotate ? Math.PI / 4 : 0), getColorStyle("backgroundColor"));
		setColor(configureGeometry(geometry2, -1, rotate ? Math.PI / 4 : 0), getColorStyle("textColor"));
		setColor(configureGeometry(geometry3, 0, rotate ? Math.PI / 4 : 0), getColorStyle("backgroundColor"));
		const blob = new THREE.Mesh(BufferGeometryUtils.mergeGeometries([geometry1, geometry2, geometry3], false), materialWithVertexColors);

		const update = () => {
			const [canvasX, canvasY] = getCoordinates(x, z);
			blob.position.x = canvasX;
			blob.position.y = -canvasY;
			const halfCanvasWidth = canvasElement.clientWidth / 2;
			const halfCanvasHeight = canvasElement.clientHeight / 2;
			if (renderedTextCount < SETTINGS.maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
				element.style.transform = `translate(-50%,0)translate(${canvasX + halfCanvasWidth}px,${canvasY + halfCanvasHeight + textOffset}px)`;
				element.style.display = "";
				renderedTextCount++;
			} else {
				element.style.display = "none";
			}
		};

		update();
		addCanvasCallback(update);
		scene.add(blob);
	}, (connections, connectionsCount) => {
		const geometry = new THREE.BufferGeometry();
		const count = 54;
		const positionAttribute = new THREE.BufferAttribute(new Float32Array(connectionsCount * count * 3), 3);
		geometry.setAttribute("position", positionAttribute);
		const colorAttribute = configureGeometry(geometry);
		let lineIndex = 0;

		connections.forEach(connection => {
			const {colorsAndOffsets, direction1, direction2, x1, z1, x2, z2} = connection;

			for (let i = 0; i < colorsAndOffsets.length; i++) {
				const {color, offset1, offset2} = colorsAndOffsets[i];
				const lineArrayIndex = lineIndex * count;
				const colorOffset = (i - colorsAndOffsets.length / 2 + 0.5) * 6 * SETTINGS.scale;

				for (let j = 0; j < count; j++) {
					setColorByIndex(colorAttribute, color, lineArrayIndex + j);
				}

				const update = () => connectStations(
					positionAttribute,
					lineArrayIndex,
					count,
					getXCoordinate(x1),
					getYCoordinate(z1),
					getXCoordinate(x2),
					getYCoordinate(z2),
					direction1,
					direction2,
					offset1 * 6 * SETTINGS.scale,
					offset2 * 6 * SETTINGS.scale,
					colorOffset
				);

				update();
				addCanvasCallback(update);
				lineIndex++;
			}
		});

		for (let i = 0; i < positionAttribute.count; i++) {
			console.assert(positionAttribute.getZ(i) !== 0, "Position attribute not populated", i);
		}

		addCanvasCallback(() => {
			positionAttribute.needsUpdate = true;
			geometry.computeBoundingSphere();
		});
		scene.add(new THREE.Mesh(geometry, materialWithVertexColors));
	}, resize);
}

main();
