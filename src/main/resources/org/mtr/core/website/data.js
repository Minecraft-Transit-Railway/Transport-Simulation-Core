export function rotate(x, z, direction) {
	const angle = direction * Math.PI / 4;
	const cos = Math.cos(angle);
	const sin = Math.sin(angle);
	return [x * cos + z * sin, z * cos - x * sin];
}

export function transform(data) {
	const {routes, stations} = data;
	const sortedStations = [];
	const connections = {};

	routes.forEach(route => {
		const routePositions = [];
		route.stations.forEach(stationId => routePositions.push(stations[stationId]));
		route.positions = routePositions;
		route.directions = [];

		for (let i = 0; i < routePositions.length; i++) {
			const currentStation = routePositions[i];
			const station1 = routePositions[i - 1];
			const station2 = routePositions[i + 1];
			const station3 = station1 === undefined ? currentStation : station1;
			const station4 = station2 === undefined ? currentStation : station2;
			const direction = (Math.round(Math.atan2(station4.x - station3.x, station4.z - station3.z) * 4 / Math.PI) + 8) % 4;
			route.directions.push(direction);
			if (currentStation.routes === undefined) {
				currentStation.routes = [[], [], [], []];
			}
			if (!currentStation.routes[direction].includes(route.color)) {
				currentStation.routes[direction].push(route.color);
			}
		}
	});

	Object.entries(stations).forEach(stationEntry => {
		const [stationId, station] = stationEntry;
		for (let i = 0; i < station.routes.length; i++) {
			station.routes[i] = station.routes[i].sort();
		}
		const routesForDirection = station.routes;
		const rotate = routesForDirection[1].length + routesForDirection[3].length > routesForDirection[0].length + routesForDirection[2].length;
		sortedStations.push({
			stationId,
			name: station.name,
			routeCount: station.routes.reduce((previousValue, currentElement) => previousValue + currentElement.length, 0),
			x: station.x,
			z: station.z,
			rotate,
			width: Math.max(0, routesForDirection[rotate ? 1 : 0].length - 1),
			height: Math.max(0, routesForDirection[rotate ? 3 : 2].length - 1),
		});
	});

	routes.forEach(route => {
		const {color, directions, positions} = route;
		for (let i = 1; i < directions.length; i++) {
			const reverse = route.stations[i - 1] > route.stations[i];
			const index1 = reverse ? i : i - 1;
			const index2 = reverse ? i - 1 : i;
			const key = `${route.stations[index1]}_${route.stations[index2]}_${color}`;
			if (connections[key] === undefined) {
				const direction1 = directions[index1];
				const direction2 = directions[index2];
				const routesForDirection1 = positions[index1].routes[direction1];
				const routesForDirection2 = positions[index2].routes[direction2];
				const offset1 = routesForDirection1.indexOf(color) - (routesForDirection1.length - 1) / 2;
				const offset2 = routesForDirection2.indexOf(color) - (routesForDirection2.length - 1) / 2;
				connections[key] = {
					color,
					direction1,
					direction2,
					x1: positions[index1].x,
					z1: positions[index1].z,
					x2: positions[index2].x,
					z2: positions[index2].z,
					offsetX1: offset1 * getOffsetX(direction1),
					offsetY1: offset1 * getOffsetZ(direction1),
					offsetX2: offset2 * getOffsetX(direction2),
					offsetY2: offset2 * getOffsetZ(direction2),
				};
			}
		}
	});

	return [sortedStations.sort((station1, station2) => {
		if (station1.routeCount === station2.routeCount) {
			return station2.name.length - station1.name.length;
		} else {
			return station2.routeCount - station1.routeCount;
		}
	}), connections];
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

function getOffsetZ(direction) {
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
