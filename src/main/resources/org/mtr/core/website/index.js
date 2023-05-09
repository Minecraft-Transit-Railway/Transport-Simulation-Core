import {
	animateCenter,
	getCurrentWindowValues,
	setCenterOnFirstDraw,
	setLoading,
	setMouseCallback,
	setResizeCallback,
} from "./mouse.js";
import {getData, updateData} from "./data.js";
import {getColorStyle, getRouteTypeIcon, isCJK} from "./utilities.js";
import Callback from "./callback.js";
import SETTINGS from "./settings.js";

const callback = new Callback();
const canvasElement = document.querySelector("#canvas");
const containerLabelsElement = document.querySelector("#container-labels");
const mainPanelElement = document.querySelector("#main-panel");
const searchElement = document.querySelector("#input-search");
const buttonSearchElement = document.querySelector("#button-search");
const buttonClearSearchElement = document.querySelector("#button-clear-search");
const containerRouteTypesElement = document.querySelector("#container-route-types");
const buttonCenterMapElement = document.querySelector("#button-center-map");

function setup() {
	setMainPanelWidth();
	searchElement.onchange = onSearch;
	searchElement.onpaste = onSearch;
	searchElement.oninput = onSearch;
	buttonSearchElement.onclick = () => searchElement.focus();
	buttonClearSearchElement.onclick = () => clearSearch(true);
}

function main() {
	const offscreen = canvasElement.transferControlToOffscreen();
	const worker = new Worker("offscreen.js", {type: "module"});
	worker.postMessage({type: "setup", canvas: offscreen}, [offscreen]);
	worker.onmessage = () => setLoading(false);
	let renderedTextCount = 0;

	setMouseCallback((zoom, centerX, centerY) => {
		worker.postMessage({type: "draw", zoom, centerX, centerY});
		callback.update([zoom, centerX, centerY]);
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
		callback.update([zoom, centerX, centerY]);
		setMainPanelWidth();
	});

	const draw = (initialCenterX, initialCenterY, stations, routeTypes, connectionValues, connectionsCount) => {
		containerLabelsElement.innerHTML = "";
		callback.reset();
		callback.add(() => renderedTextCount = 0);

		stations.forEach(station => {
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
			const routeTypesIconsElement = document.createElement("p");
			routeTypesIconsElement.className = "station-name material-symbols-outlined";
			routeTypesIconsElement.innerText = types.filter(type => !SETTINGS.routeTypes.includes(type)).map(getRouteTypeIcon).join("");
			element.style.display = "none";
			element.appendChild(routeTypesIconsElement);
			containerLabelsElement.appendChild(element);

			callback.add(([zoom, centerX, centerY]) => {
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

		setCenterOnFirstDraw(initialCenterX, initialCenterY);
		buttonCenterMapElement.onclick = () => animateCenter(initialCenterX, initialCenterY);
		const [zoom, centerX, centerY] = getCurrentWindowValues();
		worker.postMessage({
			type: "main",
			stations,
			connectionValues,
			connectionsCount,
			zoom,
			centerX,
			centerY,
			canvasWidth: canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: canvasElement.clientHeight * devicePixelRatio,
			devicePixelRatio,
			backgroundColor: getColorStyle("backgroundColor"),
			textColor: getColorStyle("textColor"),
		});
		callback.update([zoom, centerX, centerY]);
		containerRouteTypesElement.innerHTML = getSpacer();

		routeTypes.forEach(routeType => {
			const routeTypeElement = document.createElement("input");
			const isSelected = () => SETTINGS.routeTypes.includes(routeType);
			const setClassName = () => routeTypeElement.className = `clickable ${isSelected() ? "selected" : ""}`
			setClassName();
			routeTypeElement.value = routeType;
			routeTypeElement.onclick = () => {
				if (isSelected()) {
					SETTINGS.routeTypes = SETTINGS.routeTypes.filter(checkRouteType => checkRouteType !== routeType);
				} else {
					SETTINGS.routeTypes.push(routeType);
				}
				setClassName();
				setLoading(true);
				updateData(draw);
			}
			containerRouteTypesElement.appendChild(routeTypeElement);
		});
	};

	getData(draw);
}

function onSearch() {
	const search = searchElement.value.toLowerCase().replace(/\|/g, " ");
	document.querySelector("#icon-clear-search").style.display = search === "" ? "none" : "";

}

function clearSearch(focus) {
	searchElement.value = "";
	if (focus) {
		searchElement.focus();
	}
	onSearch();
}

function setMainPanelWidth() {
	mainPanelElement.style.width = `${Math.min(canvasElement.clientWidth - 32, 320)}px`;
}

function getSpacer() {
	return "<div class='spacer'></div>";
}

setup();
main();
