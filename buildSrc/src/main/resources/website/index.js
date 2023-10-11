import {
	animateCenter,
	getCurrentWindowValues,
	setBlackAndWhite,
	setCenterOnFirstDraw,
	setLoading,
	setMouseCallback,
	setResizeCallback,
} from "./mouse.js";
import {getData, updateData} from "./data.js";
import {getColorStyle, getCookie, isCJK, ROUTE_TYPES, setCookie} from "./utilities.js";
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
const buttonToggleThemeElement = document.querySelector("#button-toggle-theme");
const iconToggleThemeElement = document.querySelector("#icon-toggle-theme");
const textToggleThemeElement = document.querySelector("#text-toggle-theme");
const buttonToggleTextElement = document.querySelector("#button-toggle-text");
const iconToggleTextElement = document.querySelector("#icon-toggle-text");
const textToggleTextElement = document.querySelector("#text-toggle-text");
const buttonToggleBlackAndWhiteElement = document.querySelector("#button-toggle-black-and-white");
const iconToggleBlackAndWhiteElement = document.querySelector("#icon-toggle-black-and-white");
const textToggleBlackAndWhiteElement = document.querySelector("#text-toggle-black-and-white");
const buttonInterchangeStyle1Element = document.querySelector("#button-interchange-style-1");
const buttonInterchangeStyle2Element = document.querySelector("#button-interchange-style-2");

function setup() {
	setMainPanelWidth();
	searchElement.onchange = onSearch;
	searchElement.onpaste = onSearch;
	searchElement.oninput = onSearch;
	buttonSearchElement.onclick = () => searchElement.focus();
	buttonClearSearchElement.onclick = () => clearSearch(true);
	setOrToggleTheme(true);
	setOrToggleText(true);
	buttonToggleTextElement.onclick = () => setOrToggleText();
	setOrToggleBlackAndWhite(true);
	setInterchangeStyle();
}

function main() {
	const offscreen = canvasElement.transferControlToOffscreen();
	const worker = new Worker("offscreen.js", {type: "module"});
	worker.postMessage({type: "setup", canvas: offscreen}, [offscreen]);
	worker.onmessage = () => setLoading(false);
	let renderedTextCount = 0;

	setMouseCallback((zoom, centerX, centerY, blackAndWhite) => {
		worker.postMessage({
			type: "draw",
			zoom,
			centerX,
			centerY,
			canvasWidth: canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: canvasElement.clientHeight * devicePixelRatio,
			blackAndWhite,
		});
		callback.update([zoom, centerX, centerY]);
	});

	setResizeCallback((zoom, centerX, centerY, blackAndWhite) => {
		worker.postMessage({
			type: "resize",
			zoom,
			centerX,
			centerY,
			canvasWidth: canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: canvasElement.clientHeight * devicePixelRatio,
			blackAndWhite,
			devicePixelRatio,
		});
		callback.update([zoom, centerX, centerY]);
		setMainPanelWidth();
	});

	const draw = (initialCenterX, initialCenterY, stations, routeTypes, lineConnectionValues, maxLineConnectionLength, stationConnectionValues) => {
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
			routeTypesIconsElement.innerText = types.filter(type => SETTINGS.routeTypes[type] === 0).map(type => ROUTE_TYPES[type].icon).join("");
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
		const [zoom, centerX, centerY, blackAndWhite] = getCurrentWindowValues();
		worker.postMessage({
			type: "main",
			stations,
			lineConnectionValues,
			maxLineConnectionLength,
			stationConnectionValues,
			zoom,
			centerX,
			centerY,
			blackAndWhite,
			routeTypesSettings: SETTINGS.routeTypes,
			interchangeStyle: SETTINGS.interchangeStyle,
			canvasWidth: canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: canvasElement.clientHeight * devicePixelRatio,
			devicePixelRatio,
			backgroundColor: getColorStyle("backgroundColor"),
			textColor: getColorStyle("textColor"),
		});
		callback.update([zoom, centerX, centerY]);
		containerRouteTypesElement.innerHTML = getSpacer();

		Object.keys(ROUTE_TYPES).filter(routeType => routeTypes.includes(routeType)).forEach(routeType => {
			const routeTypeElement = document.createElement("div");
			routeTypeElement.className = "one-row";
			containerRouteTypesElement.appendChild(routeTypeElement);

			const routeTypeIconElement = document.createElement("span");
			routeTypeIconElement.className = "material-symbols-outlined";
			routeTypeIconElement.innerText = ROUTE_TYPES[routeType].icon;
			routeTypeElement.appendChild(routeTypeIconElement);

			const routeTypeTextElement = document.createElement("span");
			routeTypeTextElement.innerText = ROUTE_TYPES[routeType].text;
			routeTypeElement.appendChild(routeTypeTextElement);

			const getClasses = index => `clickable ${index === 0 ? "push-right" : ""} ${SETTINGS.routeTypes[routeType] === index ? "selected" : ""}`;
			const createRouteTypeButton = (text, index) => {
				const routeTypeButtonElement = document.createElement("span");
				routeTypeButtonElement.className = getClasses(index);
				routeTypeButtonElement.innerText = text;
				routeTypeButtonElement.onclick = () => {
					for (let i = 0; i < routeTypeButtonElements.length; i++) {
						routeTypeButtonElements[i].className = getClasses(i);
					}
					SETTINGS.routeTypes[routeType] = index;
					setLoading(true);
					updateData(draw);
				};
				routeTypeElement.appendChild(routeTypeButtonElement);
				return routeTypeButtonElement;
			};

			const routeTypeButtonElements = [
				createRouteTypeButton("Hidden", 0),
				createRouteTypeButton("Solid", 1),
				createRouteTypeButton("Hollow", 2),
			];
		});
	};

	getData(draw);

	buttonToggleThemeElement.onclick = () => {
		setOrToggleTheme();
		setLoading(true);
		updateData(draw);
	};

	buttonToggleBlackAndWhiteElement.onclick = () => {
		setOrToggleBlackAndWhite();
		setLoading(true);
		updateData(draw);
	};

	buttonInterchangeStyle1Element.onclick = () => {
		setInterchangeStyle(0);
		setLoading(true);
		updateData(draw);
	};

	buttonInterchangeStyle2Element.onclick = () => {
		setInterchangeStyle(1);
		setLoading(true);
		updateData(draw);
	};
}

function onSearch() {
	const search = searchElement.value.toLowerCase().replace(/\|/g, " ");
	buttonClearSearchElement.style.display = search === "" ? "none" : "";

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

function setOrToggleSetting(cookieName, getCurrentValue, setValue, readFromCookie) {
	const shouldSet = readFromCookie ? getCookie(cookieName) === "true" : getCurrentValue();
	setCookie(cookieName, shouldSet ? "true" : "false");
	setValue(shouldSet);
}

function setOrToggleTheme(readFromCookie = false) {
	setOrToggleSetting("dark-mode", () => document.body.className === "", set => {
		document.body.className = set ? "dark-mode" : "";
		iconToggleThemeElement.innerText = set ? "light_mode" : "dark_mode";
		textToggleThemeElement.innerText = set ? "Light Mode" : "Dark Mode";
	}, readFromCookie);
}

function setOrToggleText(readFromCookie = false) {
	setOrToggleSetting("hide-text", () => containerLabelsElement.style.display === "", set => {
		containerLabelsElement.style.display = set ? "none" : "";
		iconToggleTextElement.innerText = set ? "font_download" : "font_download_off";
		textToggleTextElement.innerText = set ? "Show Station Names" : "Hide Station Names";
	}, readFromCookie);
}

function setOrToggleBlackAndWhite(readFromCookie = false) {
	setOrToggleSetting("black-and-white", () => !getCurrentWindowValues()[3], set => {
		setBlackAndWhite(set);
		iconToggleBlackAndWhiteElement.innerText = set ? "palette" : "filter_b_and_w";
		textToggleBlackAndWhiteElement.innerText = set ? "Full Colour" : "Greyscale";
	}, readFromCookie);
}

function setInterchangeStyle(style) {
	if (style === undefined) {
		style = parseInt(getCookie("interchange-style"));
		if (isNaN(style)) {
			style = 0;
		}
	}
	setCookie("interchange-style", style);
	SETTINGS.interchangeStyle = style;
	buttonInterchangeStyle1Element.className = `clickable ${style === 0 ? "selected" : ""} push-right`;
	buttonInterchangeStyle2Element.className = `clickable ${style === 1 ? "selected" : ""}`;
}

function getSpacer() {
	return "<div class='spacer'></div>";
}

setup();
main();
