import {EventEmitter, Injectable} from "@angular/core";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {Station} from "../entity/station";
import {Route, RoutePlatform} from "../entity/route";
import {DeparturesService} from "./departures.service";
import {DimensionService} from "./dimension.service";
import {MapDataService} from "./map-data.service";
import {ROUTE_TYPES} from "../data/routeType";
import {MapSelectionService} from "./map-selection.service";
import {setIfUndefined} from "../data/utilities";

const REFRESH_INTERVAL = 500;
const WALKING_SPEED = 4 / 1000; // meters per millisecond

@Injectable({providedIn: "root"})
export class DirectionsService extends SelectableDataServiceBase<{
	route?: Route,
	targetPosition: { x: number, y: number, z: number },
	departureTime: number,
	travelTime: number,
}[], {
	startPosition: { x: number, y: number, z: number },
	endPosition: { x: number, y: number, z: number },
	maxWalkingDistance: number,
}> {
	public readonly directionsPanelOpened = new EventEmitter<{ stationId: string, isStartStation: boolean } | undefined>();
	public readonly defaultMaxWalkingDistance = 250;
	private readonly newDirections: {
		startPosition: { x: number, y: number, z: number },
		startRoutePlatform?: RoutePlatform,
		endPosition: { x: number, y: number, z: number },
		endRoutePlatform?: RoutePlatform,
		intermediateRoutePlatforms: RoutePlatform[],
		route?: Route,
		icon: string,
		startTime: number,
		endTime: number,
		distance: number,
	}[] = [];
	private directionsTimeoutId = 0;

	constructor(mapDataService: MapDataService, mapSelectionService: MapSelectionService, departuresService: DeparturesService, dimensionService: DimensionService) {
		super(selectedData => {
			const dataSplit = selectedData.split("_");
			const startX = parseInt(dataSplit[0]);
			const startY = parseInt(dataSplit[1]);
			const startZ = parseInt(dataSplit[2]);
			const endX = parseInt(dataSplit[3]);
			const endY = parseInt(dataSplit[4]);
			const endZ = parseInt(dataSplit[5]);
			const maxWalkingDistance = parseInt(dataSplit[6]);
			return !isNaN(startX) && !isNaN(startY) && !isNaN(startZ) && !isNaN(endX) && !isNaN(endY) && !isNaN(endZ) ? {
				startPosition: {x: startX, y: startY, z: startZ},
				endPosition: {x: endX, y: endY, z: endZ},
				maxWalkingDistance: isNaN(maxWalkingDistance) ? this.defaultMaxWalkingDistance : maxWalkingDistance,
			} : undefined;
		}, () => {
			this.newDirections.length = 0;
			mapSelectionService.reset("directions");
			clearTimeout(this.directionsTimeoutId);
		}, ({startPosition, endPosition, maxWalkingDistance}) => {
			clearTimeout(this.directionsTimeoutId);
			const directionsCompleted = new EventEmitter<{ route?: Route, targetPosition: { x: number, y: number, z: number }, departureTime: number, travelTime: number }[]>();
			DirectionsService.createWalkingCache(
				startPosition,
				endPosition,
				maxWalkingDistance,
				directionsCompleted,
				Object.values(departuresService.getRoutePlatformsCache1()),
				{...departuresService.getDirectionsCache()},
				callback => {
					clearTimeout(this.directionsTimeoutId);
					this.directionsTimeoutId = setTimeout(() => callback(), 0) as unknown as number;
				},
			);
			return directionsCompleted;
		}, directions => {
			this.newDirections.length = 0;
			const routePlatformsCache = departuresService.getRoutePlatformsCache2();

			for (let i = 1; i < directions.length; i++) {
				const previousDirection = directions[i - 1];
				const thisDirection = directions[i];

				if (i > 1 && previousDirection.route?.id === thisDirection.route?.id) {
					const lastNewDirection = this.newDirections[this.newDirections.length - 1];
					lastNewDirection.endPosition = thisDirection.targetPosition;
					if (lastNewDirection.endRoutePlatform) {
						lastNewDirection.intermediateRoutePlatforms.push(lastNewDirection.endRoutePlatform);
					}
					lastNewDirection.endRoutePlatform = routePlatformsCache[DeparturesService.getPositionKey(thisDirection.targetPosition)];
					lastNewDirection.endTime = thisDirection.departureTime + thisDirection.travelTime;
				} else {
					const startTime = i === 1 && !thisDirection.route && i + 1 < directions.length ? directions[i + 1].departureTime - thisDirection.travelTime : thisDirection.departureTime;
					this.newDirections.push({
						startPosition: previousDirection.targetPosition,
						startRoutePlatform: routePlatformsCache[DeparturesService.getPositionKey(previousDirection.targetPosition)],
						endPosition: thisDirection.targetPosition,
						endRoutePlatform: routePlatformsCache[DeparturesService.getPositionKey(thisDirection.targetPosition)],
						intermediateRoutePlatforms: [],
						route: thisDirection.route,
						icon: thisDirection.route ? ROUTE_TYPES[thisDirection.route.type].icon : "",
						startTime,
						endTime: startTime + thisDirection.travelTime,
						distance: DeparturesService.getDistance(previousDirection.targetPosition, thisDirection.targetPosition),
					});
				}
			}
		}, REFRESH_INTERVAL, dimensionService);
	}

	public selectStations(startPosition: { x: number, y: number, z: number }, endPosition: { x: number, y: number, z: number }, maxWalkingDistanceString: string) {
		const key = `${DeparturesService.getPositionKey(startPosition)}_${DeparturesService.getPositionKey(endPosition)}_${maxWalkingDistanceString}`;
		this.select(key);
		this.fetchData(key);
	}

	public getDirections() {
		return this.newDirections;
	}

	private static createWalkingCache(
		startPosition: { x: number, y: number, z: number },
		endPosition: { x: number, y: number, z: number },
		maxWalkingDistance: number,
		directionsCompleted: EventEmitter<{ route?: Route, targetPosition: { x: number, y: number, z: number }, departureTime: number, travelTime: number }[]>,
		routePlatformsCache: { station?: Station, routePlatforms: { [positionKey: string]: { x: number, y: number, z: number } } }[],
		directionsCache: { [positionKey: string]: { [targetPositionKey: string]: { targetPosition: { x: number, y: number, z: number }, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } },
		setTimeout: (callback: () => void) => void,
	) {
		const startPositionKey = DeparturesService.getPositionKey(startPosition);
		routePlatformsCache.push({routePlatforms: {[startPositionKey]: {...startPosition}}});

		const endPositionKey = DeparturesService.getPositionKey(endPosition);
		routePlatformsCache.push({routePlatforms: {[endPositionKey]: {...endPosition}}});

		const createWalkingCachePart = (index1: number, index2: number, iterations: number, startTime: number) => {
			const startLoopTime = Date.now();

			while (true) {
				if (index2 >= routePlatformsCache.length) {
					index2 = 0;
					index1++;
				}

				if (index1 >= routePlatformsCache.length) {
					console.debug(`Cache for walking distances between ${index1} platform(s) created after ${iterations} iteration(s) in ${Date.now() - startTime} ms`);
					setTimeout(() => this.findDirections(startPosition, endPosition, directionsCompleted, directionsCache, [], {}, Date.now(), Number.MAX_SAFE_INTEGER, 0, setTimeout));
					break;
				}

				const positionCache1: { station?: Station, routePlatforms: { [positionKey: string]: { x: number, y: number, z: number } } } = routePlatformsCache[index1];
				const positionCache2: { station?: Station, routePlatforms: { [positionKey: string]: { x: number, y: number, z: number } } } = routePlatformsCache[index2];

				// Cache walking distances between platforms if the stations are close together, if the stations are overlapping, or if the positions are start or end positions
				if (
					DeparturesService.getDistance(positionCache1.station ?? Object.values(positionCache1.routePlatforms)[0], positionCache2.station ?? Object.values(positionCache2.routePlatforms)[0]) <= maxWalkingDistance ||
					positionCache1.station && positionCache2.station && positionCache1.station.connections.some(connectingStation => connectingStation.id === positionCache2.station?.id) ||
					index1 >= routePlatformsCache.length - 2 || index2 >= routePlatformsCache.length - 2
				) {
					const routePlatforms1 = Object.entries(positionCache1.routePlatforms);
					const routePlatforms2 = Object.entries(positionCache2.routePlatforms);
					for (let i = 0; i < routePlatforms1.length + routePlatforms2.length; i++) {
						for (let j = 0; j < routePlatforms1.length + routePlatforms2.length; j++) {
							const [positionKey1, position1] = i < routePlatforms1.length ? routePlatforms1[i] : routePlatforms2[i - routePlatforms1.length];
							const [positionKey2, position2] = j < routePlatforms1.length ? routePlatforms1[j] : routePlatforms2[j - routePlatforms1.length];
							if (positionKey1 !== positionKey2) {
								const distance = DeparturesService.getDistance(position1, position2);
								if (distance <= maxWalkingDistance) {
									setIfUndefined(directionsCache, positionKey1, () => ({}));
									setIfUndefined(directionsCache[positionKey1], positionKey2, () => ({targetPosition: position2, connections: {}}));
									directionsCache[positionKey1][positionKey2].connections[""] = {departureTimes: [], travelTime: Math.round(distance / WALKING_SPEED)};
								}
							}
						}
					}
				}

				index2++;

				if (Date.now() - startLoopTime >= 10) {
					setTimeout(() => createWalkingCachePart(index1, index2, iterations + 1, startTime));
					break;
				}
			}
		};

		createWalkingCachePart(0, 0, 0, Date.now());
	}

	// Actual pathfinding logic
	private static findDirections(
		startPosition: { x: number, y: number, z: number },
		endPosition: { x: number, y: number, z: number },
		directionsCompleted: EventEmitter<{ route?: Route, targetPosition: { x: number, y: number, z: number }, departureTime: number, travelTime: number }[]>,
		directionsCache: { [positionKey: string]: { [targetPositionKey: string]: { targetPosition: { x: number, y: number, z: number }, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } },
		directions: { route?: Route, targetPosition: { x: number, y: number, z: number }, departureTime: number, travelTime: number }[],
		globalPositionTimes: { [positionKey: string]: number },
		startTime: number,
		endTime: number,
		iterations: number,
		setTimeout: (callback: () => void) => void,
	) {
		const tempDirections: {
			route?: Route,
			targetPosition: { x: number, y: number, z: number },
			departureTime: number,
			travelTime: number,
		}[] = [{targetPosition: startPosition, departureTime: startTime, travelTime: 0}];
		const startPositionKey = DeparturesService.getPositionKey(startPosition);
		const endPositionKey = DeparturesService.getPositionKey(endPosition);
		const localPositionTimes: { [positionKey: string]: number } = {};
		globalPositionTimes[startPositionKey] = 0;

		const findDirectionsSegment = (innerIterations: number) => {
			const startLoopTime = Date.now();

			while (true) {
				const lastDirection = tempDirections[tempDirections.length - 1];
				const currentPosition = lastDirection.targetPosition;
				const currentPositionKey = DeparturesService.getPositionKey(currentPosition);
				const currentTime = lastDirection.departureTime + lastDirection.travelTime;

				if (currentPositionKey === endPositionKey) {
					setTimeout(() => this.findDirections(startPosition, endPosition, directionsCompleted, directionsCache, tempDirections, globalPositionTimes, startTime, currentTime, iterations + 1, setTimeout));
					break;
				}

				const currentDistance = DeparturesService.getDistance(currentPosition, endPosition);
				const bestData: { increase: number, connection?: { route?: Route, targetPosition: { x: number, y: number, z: number }, targetPositionKey: string, departureTime: number, travelTime: number } } = {increase: -Number.MAX_SAFE_INTEGER};

				if (Object.prototype.hasOwnProperty.call(directionsCache, currentPositionKey)) {
					const directionsCacheForPosition = directionsCache[currentPositionKey];
					for (const directionsCacheForPositionKey in directionsCacheForPosition) {
						if (Object.prototype.hasOwnProperty.call(directionsCacheForPosition, directionsCacheForPositionKey)) {
							const {targetPosition, connections} = directionsCacheForPosition[directionsCacheForPositionKey];
							for (const connectionKey in connections) {
								if (Object.prototype.hasOwnProperty.call(connections, connectionKey)) {
									const connection = connections[connectionKey];
									// Don't allow two walking directions in a row
									if (currentPositionKey === startPositionKey || connection.route || lastDirection.route) {
										const newDepartureTime = connection.route ? DirectionsService.getBestDepartureTime(connection.departureTimes, currentTime) : currentTime;
										if (newDepartureTime >= 0) {
											const arrivalTime = newDepartureTime + connection.travelTime;
											const targetPositionKey = DeparturesService.getPositionKey(targetPosition);
											if (arrivalTime < endTime && arrivalTime < (localPositionTimes[targetPositionKey] ?? Number.MAX_SAFE_INTEGER) && arrivalTime <= (globalPositionTimes[targetPositionKey] ?? Number.MAX_SAFE_INTEGER)) {
												const increase = (currentDistance - DeparturesService.getDistance(targetPosition, endPosition)) / (arrivalTime - currentTime);
												globalPositionTimes[targetPositionKey] = arrivalTime;
												if (increase > bestData.increase) {
													bestData.increase = increase;
													bestData.connection = {route: connection.route, targetPosition, targetPositionKey, departureTime: newDepartureTime, travelTime: connection.travelTime};
												}
											}
										}
									}
								}
							}
						}
					}
				}

				if (bestData.connection) {
					tempDirections.push(bestData.connection);
					localPositionTimes[bestData.connection.targetPositionKey] = bestData.connection.departureTime + bestData.connection.travelTime;
				} else {
					tempDirections.pop();
					if (tempDirections.length === 0) {
						console.debug(`Directions found after ${iterations} iteration(s) and ${innerIterations} inner iteration(s) in ${Date.now() - startTime} ms`);
						directionsCompleted.emit(directions);
						break;
					}
				}

				if (Date.now() - startLoopTime >= 10) {
					setTimeout(() => findDirectionsSegment(innerIterations + 1));
					break;
				}
			}
		};

		findDirectionsSegment(0);
	}

	// Find the lowest value in the sorted list of departures that is greater than the current time
	private static getBestDepartureTime(departureTimes: number[], currentTime: number) {
		let left = 0;
		let right = departureTimes.length - 1;
		let result = -1;

		while (left <= right) {
			const mid = Math.floor((left + right) / 2);
			if (departureTimes[mid] > currentTime) {
				result = departureTimes[mid];
				right = mid - 1;
			} else {
				left = mid + 1;
			}
		}

		return result;
	}
}
