import {pushIfNotExists} from "./utilities.js";

export function transform(data) {
	const {routes, stations} = data;

	routes.forEach(route => {
		const routeStations = [];
		route.stations.forEach(stationId => routeStations.push(stations.find(station => station.id === stationId)));
		route.stations = routeStations;

		const iterateStations = reverse => {
			for (let i = 0; i < routeStations.length; i++) {
				const index = reverse ? routeStations.length - i - 1 : i;
				const currentStation = routeStations[index];
				const station1 = routeStations[index + (reverse ? 1 : -1)];
				const station2 = routeStations[index + (reverse ? -1 : 1)];
				const station3 = station1 === undefined ? currentStation : station1;
				const station4 = station2 === undefined ? currentStation : station2;
				const direction = (Math.round(Math.atan2(station4.x - station3.x, station4.z - station3.z) * 4 / Math.PI) + 8) % 4;
				const routeColorString = route.color.toString();

				if (currentStation.routes === undefined || currentStation.groups === undefined) {
					currentStation.routes = {};
					currentStation.groups = {};
				}

				if (currentStation.routes[routeColorString] === undefined) {
					currentStation.routes[routeColorString] = [];
				}
				currentStation.routes[routeColorString].push(direction);

				if (station2 !== undefined) {
					const nextStationId = station2.id;
					if (currentStation.groups[nextStationId] === undefined) {
						currentStation.groups[nextStationId] = [];
					}
					pushIfNotExists(currentStation.groups[nextStationId], route.color);
				}
			}
		};

		iterateStations(false);
		iterateStations(true);
	});

	const connections = {};
	let connectionsCount = 0;
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
			if (testRouteColors.includes(routeColor)) {
				console.error("Duplicates in combined groups", combinedGroups);
			}
			testRouteColors.push(routeColor);
		}));

		const routesForDirection = [[], [], [], []];
		combinedGroups.forEach(combinedGroup => {
			const directionsCount = [0, 0, 0, 0];
			combinedGroup.forEach(routeColor => station.routes[routeColor.toString()].forEach(direction => directionsCount[direction]++));
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
				station.routes[routeColor.toString()] = direction;
			});
		});

		const rotate = routesForDirection[1].length + routesForDirection[3].length > routesForDirection[0].length + routesForDirection[2].length;
		Object.assign(station, {
			rotate,
			routeCount: Object.keys(station.routes).length,
			width: Math.max(0, routesForDirection[rotate ? 1 : 0].length - 1),
			height: Math.max(0, routesForDirection[rotate ? 3 : 2].length - 1),
		});

		Object.entries(station.groups).forEach(groupEntry => {
			const [stationId, routeColors] = groupEntry;
			if (station.id !== stationId) {
				const reverse = station.id > stationId;
				const key = `${reverse ? stationId : station.id}_${reverse ? station.id : stationId}`;
				const [firstRouteColor] = routeColors;
				if (connections[key] === undefined) {
					connections[key] = {colors: routeColors};
					connectionsCount += routeColors.length;
				}
				if (connections[key].colors.length !== routeColors.length) {
					console.error("Colors mismatch", station);
				}
				const index = reverse ? 2 : 1;
				const direction = station.routes[firstRouteColor.toString()];
				const offset = routesForDirection[direction].indexOf(firstRouteColor) - routesForDirection[direction].length / 2 + routeColors.length / 2;
				connections[key][`direction${index}`] = direction;
				connections[key][`x${index}`] = station.x;
				connections[key][`z${index}`] = station.z;
				connections[key][`offsetX${index}`] = offset * getOffsetX(direction);
				connections[key][`offsetY${index}`] = offset * getOffsetY(direction);
			}
		});
	});

	stations.sort((station1, station2) => {
		if (station1.routeCount === station2.routeCount) {
			return station2.name.length - station1.name.length;
		} else {
			return station2.routeCount - station1.routeCount;
		}
	});

	Object.values(connections).forEach(connection => {
		if (connection.x1 === undefined || connection.x2 === undefined) {
			console.error("Incomplete connection", connection);
		}
	});

	return [connections, connectionsCount];
}

function getOffsetX(direction) {
	switch (direction % 4) {
		case 0:
			return 1;
		case 1:
			return Math.SQRT1_2;
		case 2:
			return 0;
		case 3:
			return Math.SQRT1_2;
	}
}

function getOffsetY(direction) {
	switch (direction % 4) {
		case 0:
			return 0;
		case 1:
			return -Math.SQRT1_2;
		case 2:
			return 1;
		case 3:
			return Math.SQRT1_2;
	}
}
