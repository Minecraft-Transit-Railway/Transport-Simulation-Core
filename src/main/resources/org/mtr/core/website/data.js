import {pushIfNotExists, setIfUndefined} from "./utilities.js";
import SETTINGS from "./settings.js";

const url = `${document.location.origin}${document.location.pathname.replace("index.html", "")}mtr/api/data/`;
let stations = [];
let routes = [];
let connections = {};
let connectionsCount = 0;

export function getData(callback) {
	fetch(url + "stations-and-routes").then(data => data.json()).then(data => {
		routes = [];

		data.data.routes.forEach(route => {
			const routeStations = [];
			route.stations.forEach(stationId => {
				const station = data.data.stations.find(station => station.id === stationId);
				if (station !== undefined) {
					routeStations.push(station);
				}
			});
			route.stations = routeStations;
			if (routeStations.length > 0) {
				routes.push(route);
			}
		});

		const [centerX, centerY] = updateData();
		callback(centerX, centerY);
	});
}

export function iterateData(stationCallback, connectionsCallback) {
	const connectionValues = Object.values(connections);
	if (stationCallback !== undefined) {
		stations.forEach(stationCallback);
	}
	if (connectionsCallback !== undefined) {
		connectionValues.forEach(connection => console.assert(connection.x1 !== undefined && connection.x2 !== undefined, "Incomplete connection", connection));
		connectionsCallback(connectionValues);
	}
	return [stations, connectionValues, connectionsCount];
}

function updateData() {
	stations = [];
	connections = {};
	connectionsCount = 0;

	routes.forEach(route => {
		const iterateStations = reverse => {
			const routeStations = route.stations;
			for (let i = 0; i < routeStations.length; i++) {
				const index = reverse ? routeStations.length - i - 1 : i;
				const currentStation = routeStations[index];
				const station1 = routeStations[index + (reverse ? 1 : -1)];
				const station2 = routeStations[index + (reverse ? -1 : 1)];
				const station3 = station1 === undefined ? currentStation : station1;
				const station4 = station2 === undefined ? currentStation : station2;

				if (currentStation.routes === undefined || currentStation.groups === undefined || currentStation.types === undefined) {
					currentStation.routes = {};
					currentStation.groups = {};
					currentStation.types = [];
				}

				if (SETTINGS.routeTypes.includes(route.type)) {
					setIfUndefined(currentStation.routes, route.color, []);
					currentStation.routes[route.color].push((Math.round(Math.atan2(station4.x - station3.x, station4.z - station3.z) * 4 / Math.PI) + 8) % 4);

					if (station2 !== undefined) {
						const nextStationId = station2.id;
						if (nextStationId !== currentStation.id) {
							setIfUndefined(currentStation.groups, nextStationId, []);
							pushIfNotExists(currentStation.groups[nextStationId], route.color);
						}
					}

					pushIfNotExists(stations, currentStation);
				} else {
					pushIfNotExists(currentStation.types, route.type);
				}
			}
		};

		iterateStations(false);
		iterateStations(true);
	});

	stations.forEach(station => {
		const combinedGroups = [];
		Object.entries(station.groups).forEach(groupEntry1 => {
			const [, routeColors1] = groupEntry1;
			const combinedGroup = [...routeColors1];
			Object.entries(station.groups).forEach(groupEntry2 => {
				const [, routeColors2] = groupEntry2;
				if (combinedGroup.some(routeColor => routeColors2.includes(routeColor))) {
					routeColors2.forEach(routeColor => pushIfNotExists(combinedGroup, routeColor));
				}
			});
			const groupToAddTo = combinedGroups.find(existingCombinedGroup => existingCombinedGroup.some(routeColor => combinedGroup.includes(routeColor)));
			if (groupToAddTo === undefined) {
				combinedGroups.push(combinedGroup);
			} else {
				combinedGroup.forEach(routeColor => pushIfNotExists(groupToAddTo, routeColor));
			}
		});

		const testRouteColors = [];
		combinedGroups.forEach(combinedGroup => combinedGroup.forEach(routeColor => {
			console.assert(!testRouteColors.includes(routeColor), "Duplicate colors in combined groups", combinedGroups);
			testRouteColors.push(routeColor);
		}));

		const routesForDirection = [[], [], [], []];
		combinedGroups.forEach(combinedGroup => {
			const directionsCount = [0, 0, 0, 0];
			combinedGroup.forEach(routeColor => station.routes[routeColor].forEach(direction => directionsCount[direction]++));
			let direction = 0;
			let max = 0;

			for (let i = 0; i < 4; i++) {
				if (directionsCount[i] > max) {
					direction = i;
					max = directionsCount[i];
				}
			}

			combinedGroup.forEach(routeColor => {
				routesForDirection[direction].push(routeColor);
				station.routes[routeColor] = direction;
			});
		});

		const rotate = routesForDirection[1].length + routesForDirection[3].length > routesForDirection[0].length + routesForDirection[2].length;
		Object.assign(station, {
			rotate,
			routeCount: Object.keys(station.routes).length,
			width: Math.max(0, routesForDirection[rotate ? 1 : 0].length - 1),
			height: Math.max(0, routesForDirection[rotate ? 3 : 2].length - 1),
		});

		routesForDirection.forEach(routesForOneDirection => routesForOneDirection.sort());

		Object.entries(station.groups).forEach(groupEntry => {
			const [stationId, routeColors] = groupEntry;
			const reverse = station.id > stationId;
			const key = `${reverse ? stationId : station.id}_${reverse ? station.id : stationId}`;
			routeColors.sort();
			setIfUndefined(connections, key, {colorsAndOffsets: routeColors.map(() => Object.create(null))}, () => connectionsCount += routeColors.length);
			const index = reverse ? 2 : 1;
			const direction = station.routes[routeColors[0]];
			const connection = connections[key];
			connection[`direction${index}`] = direction;
			connection[`x${index}`] = station.x;
			connection[`z${index}`] = station.z;
			for (let i = 0; i < routeColors.length; i++) {
				const color = routeColors[i];
				const colorAndOffset = connection.colorsAndOffsets[i];
				console.assert(colorAndOffset.color === undefined || colorAndOffset.color === color, "Color and offsets mismatch", colorAndOffset);
				colorAndOffset.color = color;
				colorAndOffset[`offset${index}`] = routesForDirection[direction].indexOf(color) - routesForDirection[direction].length / 2 + 0.5;
			}
		});

		delete station.groups;
		delete station.routes;
	});

	stations.sort((station1, station2) => {
		if (station1.routeCount === station2.routeCount) {
			return station2.name.length - station1.name.length;
		} else {
			return station2.routeCount - station1.routeCount;
		}
	});

	let closestDistance;
	let centerX = 0;
	let centerY = 0;
	stations.forEach(station => {
		const distance = Math.abs(station.x) + Math.abs(station.z);
		if (closestDistance === undefined || distance < closestDistance) {
			closestDistance = distance;
			centerX = -station.x;
			centerY = -station.z;
		}
	});

	return [centerX, centerY];
}
