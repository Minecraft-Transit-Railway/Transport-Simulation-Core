import {setCenter, setMouseCallback, setResizeCallback} from "./mouse.js";
import {getData, iterateData} from "./data.js";
import {getColorStyle, getRouteTypeIcon, isCJK} from "./utilities.js";
import Callback from "./callback.js";
import SETTINGS from "./settings.js";

const callback = new Callback();
const loadingElement = document.querySelector("#loading");
const canvasElement = document.querySelector("#canvas");
const labelElement = document.querySelector("#labels");

function main() {
	const offscreen = canvasElement.transferControlToOffscreen();
	const worker = new Worker("offscreen.js", {type: "module"});
	worker.postMessage({type: "setup", canvas: offscreen}, [offscreen]);
	let renderedTextCount = 0;

	setMouseCallback((zoom, centerX, centerY) => {
		worker.postMessage({type: "draw", zoom, centerX, centerY});
		callback.update(zoom, centerX, centerY);
	});

	setResizeCallback((zoom, centerX, centerY) => {
		worker.postMessage({
			type: "resize",
			zoom,
			centerX,
			centerY,
			canvasWidth: canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: canvasElement.clientHeight * devicePixelRatio,
			devicePixelRatio,
		});
		callback.update(zoom, centerX, centerY);
	});

	getData((initialCenterX, initialCenterY) => {
		labelElement.innerHTML = "";
		callback.reset();
		callback.add(() => renderedTextCount = 0);
		setCenter(initialCenterX, initialCenterY);

		const [stations, connectionValues, connectionsCount] = iterateData(station => {
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
			element.style.display = "none";
			element.appendChild(routeTypesElement);
			labelElement.appendChild(element);

			callback.add((zoom, centerX, centerY) => {
				const canvasX = x * zoom + centerX;
				const canvasY = z * zoom + centerY;
				const halfCanvasWidth = canvasElement.clientWidth / 2;
				const halfCanvasHeight = canvasElement.clientHeight / 2;
				if (renderedTextCount < SETTINGS.maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
					element.style.transform = `translate(-50%,0)translate(${canvasX + halfCanvasWidth}px,${canvasY + halfCanvasHeight + textOffset}px)`;
					element.style.display = "";
					renderedTextCount++;
				} else {
					element.style.display = "none";
				}
			});
		});

		worker.postMessage({
			type: "main",
			stations,
			connectionValues,
			connectionsCount,
			zoom: 1,
			centerX: initialCenterX,
			centerY: initialCenterY,
			canvasWidth: canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: canvasElement.clientHeight * devicePixelRatio,
			devicePixelRatio,
			backgroundColor: getColorStyle("backgroundColor"),
			textColor: getColorStyle("textColor"),
		});

		callback.update(1, initialCenterX, initialCenterY);
		loadingElement.style.opacity = "0";
	});
}

main();
