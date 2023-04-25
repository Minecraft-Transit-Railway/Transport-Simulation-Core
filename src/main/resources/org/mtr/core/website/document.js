import VERSION from "./version.js";
import DATA from "./data.js";
import SETTINGS from "./settings.js";
import DRAWING from "./drawing.js";
import DIRECTIONS from "./directions.js";

const DOCUMENT = {
	clearPanes: clearSelection => {
		if (clearSelection) {
			SETTINGS.selectedStation = 0;
			SETTINGS.selectedRoutes = [];
			SETTINGS.selectedDirectionsStations = [];
			SETTINGS.selectedDirectionsSegments = {};
		}
		showSettings = false;
		document.getElementById("station-info").style.display = "none";
		document.getElementById("route-info").style.display = "none";
		document.getElementById("directions").style.display = "none";
		document.getElementById("settings").style.display = "none";
		DIRECTIONS.stopRefresh();
	},
	onClearSearch: focus => {
		const searchBox = document.getElementById("search-box");
		searchBox.value = "";
		if (focus) {
			searchBox.focus();
		}
		document.getElementById("clear-search-icon").style.display = "none";
		const {stations, routes} = DATA.json[SETTINGS.dimension];
		for (const stationId in stations) {
			document.getElementById("search-station-" + stationId).style.display = "none";
		}
		for (const index in routes) {
			document.getElementById("search-route-" + routes[index]["id"]).style.display = "none";
		}
	},
	onSearch: () => {
		const searchBox = document.getElementById("search-box");
		const search = searchBox.value.toLowerCase().replace(/\|/g, " ");
		document.getElementById("clear-search-icon").style.display = search === "" ? "none" : "";

		const {stations, routes} = DATA.json[SETTINGS.dimension];

		const resultsStations = search === "" ? [] : Object.keys(stations).filter(station => stations[station]["name"].replace(/\|/g, " ").toLowerCase().includes(search));
		for (const stationId in stations) {
			document.getElementById("search-station-" + stationId).style.display = resultsStations.includes(stationId) ? "block" : "none";
		}

		const resultsRoutes = search === "" ? [] : Object.keys(routes).filter(route => routes[route]["name"].toLowerCase().includes(search));
		for (const routeIndex in routes) {
			document.getElementById("search-route-" + routes[routeIndex]["id"]).style.display = resultsRoutes.includes(routeIndex) ? "block" : "none";
		}

		const maxHeight = (window.innerHeight - 80) / 2;
		document.getElementById("search-results-stations").style.maxHeight = maxHeight + "px";
		document.getElementById("search-results-routes").style.maxHeight = maxHeight + "px";

		if (search !== "") {
			DOCUMENT.clearPanes(false);
		}
	},
};

const getCookie = name => {
	const nameFind = name + "=";
	const cookiesSplit = document.cookie.split(';');
	for (let cookie of cookiesSplit) {
		while (cookie.charAt(0) === " ") {
			cookie = cookie.substring(1);
		}
		if (cookie.indexOf(nameFind) === 0) {
			return cookie.substring(nameFind.length, cookie.length);
		}
	}
	return "";
};
const setCookie = (name, value) => document.cookie = name + "=" + value + ";expires=Fri, 31 Dec 9999 23:59:59 GMT;path=/";

let showSettings = false;

if (getCookie("theme").includes("dark")) {
	document.body.setAttribute("class", "dark-theme");
	document.getElementById("toggle-theme-icon").innerText = "light-mode";
} else {
	document.getElementById("toggle-theme-icon").innerText = "dark-mode";
}

const SEARCH_BOX_ELEMENT = document.getElementById("search-box");
SEARCH_BOX_ELEMENT.onchange = () => DOCUMENT.onSearch();
SEARCH_BOX_ELEMENT.onpaste = () => DOCUMENT.onSearch();
SEARCH_BOX_ELEMENT.oninput = () => DOCUMENT.onSearch();
const DIRECTIONS_BOX_1_ELEMENT = document.getElementById("directions-box-1");
const DIRECTIONS_BOX_2_ELEMENT = document.getElementById("directions-box-2");
DIRECTIONS_BOX_1_ELEMENT.onchange = () => DIRECTIONS.onSearch(1);
DIRECTIONS_BOX_1_ELEMENT.onpaste = () => DIRECTIONS.onSearch(1);
DIRECTIONS_BOX_1_ELEMENT.oninput = () => DIRECTIONS.onSearch(1);
DIRECTIONS_BOX_2_ELEMENT.onchange = () => DIRECTIONS.onSearch(2);
DIRECTIONS_BOX_2_ELEMENT.onpaste = () => DIRECTIONS.onSearch(2);
DIRECTIONS_BOX_2_ELEMENT.oninput = () => DIRECTIONS.onSearch(2);
document.getElementById("clear-search-icon").onclick = () => DOCUMENT.onClearSearch(true);
document.getElementById("zoom-in-icon").onclick = () => DRAWING.zoom(-500, window.innerWidth / 2, window.innerHeight / 2);
document.getElementById("zoom-out-icon").onclick = () => DRAWING.zoom(500, window.innerWidth / 2, window.innerHeight / 2);
document.getElementById("text-zoom-in-icon").onclick = () => {
	SETTINGS.size = Math.min(8, SETTINGS.size * 1.1);
	DATA.redraw();
};
document.getElementById("text-zoom-out-icon").onclick = () => {
	SETTINGS.size = Math.max(0.2, SETTINGS.size / 1.1);
	DATA.redraw();
};
document.getElementById("clear-directions-1-icon").onclick = () => {
	document.getElementById("directions-result").style.display = "none";
	const searchBox = document.getElementById("directions-box-1");
	searchBox.value = "";
	searchBox.focus();
};
document.getElementById("clear-directions-2-icon").onclick = () => {
	document.getElementById("directions-result").style.display = "none";
	const searchBox = document.getElementById("directions-box-2");
	searchBox.value = "";
	searchBox.focus();
};
document.getElementById("density-view-icon").onclick = event => {
	SETTINGS.densityView++;
	if (SETTINGS.densityView >= 3) {
		SETTINGS.densityView = 0;
	}
	event.target.innerText = SETTINGS.densityView === 2 ? "person-off" : SETTINGS.densityView === 1 ? "group" : "person";
	DATA.redraw();
}
document.getElementById("toggle-text-icon").onclick = event => {
	const buttonElement = event.target;
	if (buttonElement.innerText.includes("off")) {
		buttonElement.innerText = "font-download";
		SETTINGS.showText = false;
	} else {
		buttonElement.innerText = "font-download-off";
		SETTINGS.showText = true;
	}
	DATA.redraw();
};
document.getElementById("toggle-legend-icon").onclick = event => {
	const buttonElement = event.target;
	if (buttonElement.innerText.includes("remove")) {
		buttonElement.innerText = "reorder";
		SETTINGS.showLegend = false;
	} else {
		buttonElement.innerText = "playlist-remove";
		SETTINGS.showLegend = true;
	}
};
document.getElementById("toggle-theme-icon").onclick = event => {
	const buttonElement = event.target;
	if (buttonElement.innerText.includes("dark")) {
		document.body.setAttribute("class", "dark-theme");
		buttonElement.innerText = "light-mode";
		setCookie("theme", "dark");
	} else {
		document.body.removeAttribute("class");
		buttonElement.innerText = "dark-mode";
		setCookie("theme", "light");
	}
	DATA.redraw();
};
document.getElementById("settings-icon").onclick = () => {
	const newShowSettings = !showSettings;
	DOCUMENT.onClearSearch(false);
	DOCUMENT.clearPanes(false);
	document.getElementById("settings").style.display = newShowSettings ? "" : "none";
	showSettings = newShowSettings;
};
document.getElementById("clear-station-info-button").onclick = DOCUMENT.clearPanes;
document.getElementById("clear-route-info-button").onclick = DOCUMENT.clearPanes;
document.getElementById("version").innerText = VERSION;

export default DOCUMENT;
