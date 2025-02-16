import {EventEmitter, Injectable} from "@angular/core";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {Station} from "../entity/station";
import {Route} from "../entity/route";
import {DeparturesService} from "./departures.service";
import {DimensionService} from "./dimension.service";
import {MapDataService} from "./map-data.service";
import {setIfUndefined} from "../data/utilities";
import {ROUTE_TYPES} from "../data/routeType";
import {MapSelectionService} from "./map-selection.service";

const REFRESH_INTERVAL = 500;
const WALKING_SPEED = 4 / 1000; // meters per millisecond

@Injectable({providedIn: "root"})
export class DirectionsService extends SelectableDataServiceBase<{ route?: Route, targetStation: Station, departureTime: number, travelTime: number }[], { startStation: Station, endStation: Station, maxWalkingDistance: number }> {
	public readonly directionsPanelOpened = new EventEmitter<{ stationId: string, isStartStation: boolean } | undefined>();
	public readonly defaultMaxWalkingDistance = 250;
	private readonly newDirections: { startStation: Station, endStation: Station, intermediateStations: Station[], route?: Route, icon: string, startTime: number, endTime: number, distance: number }[] = [];
	private directionsTimeoutId = 0;

	constructor(mapDataService: MapDataService, mapSelectionService: MapSelectionService, private readonly departuresService: DeparturesService, dimensionService: DimensionService) {
		super(selectedData => {
			const dataSplit = selectedData.split("_");
			const startStation = mapDataService.stations.find(station => station.id === dataSplit[0]);
			const endStation = mapDataService.stations.find(station => station.id === dataSplit[1]);
			const maxWalkingDistance = parseInt(dataSplit[2]);
			return startStation && endStation ? {startStation, endStation, maxWalkingDistance: isNaN(maxWalkingDistance) ? this.defaultMaxWalkingDistance : maxWalkingDistance} : undefined;
		}, () => {
			this.newDirections.length = 0;
			mapSelectionService.reset("directions");
			clearTimeout(this.directionsTimeoutId);
		}, ({startStation, endStation, maxWalkingDistance}) => {
			clearTimeout(this.directionsTimeoutId);
			const directionsCompleted = new EventEmitter<{ route?: Route, targetStation: Station, departureTime: number, travelTime: number }[]>();
			const walkingCache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } } = {};

			const createWalkingCache = (index1: number, index2: number, iterations: number, startTime: number) => {
				const startLoopTime = Date.now();

				while (true) {
					if (index2 >= mapDataService.stations.length) {
						index2 = 0;
						index1++;
					}

					if (index1 >= mapDataService.stations.length) {
						console.debug(`Cache for walking distances between ${index1} stations(s) created after ${iterations} iteration(s) in ${Date.now() - startTime} ms`);
						this.setTimeout(() => this.findDirections(startStation, endStation, directionsCompleted, walkingCache, [], {}, Date.now(), 0));
						break;
					}

					const station = mapDataService.stations[index1];
					const targetStation = mapDataService.stations[index2];

					// Cache walking distances between stations
					if (targetStation.id !== station.id) {
						const distance = station.getDistance(targetStation);
						if (distance <= maxWalkingDistance || targetStation.connections.some(connectingStation => connectingStation.id === station.id)) {
							setIfUndefined(walkingCache, station.id, () => ({}));
							walkingCache[station.id][targetStation.id] = {targetStation, connections: {"": {departureTimes: [], travelTime: Math.round(distance / WALKING_SPEED)}}};
						}
					}

					index2++;

					if (Date.now() - startLoopTime >= 10) {
						this.setTimeout(() => createWalkingCache(index1, index2, iterations + 1, startTime));
						break;
					}
				}
			};

			createWalkingCache(0, 0, 0, Date.now());
			return directionsCompleted;
		}, directions => {
			mapSelectionService.selectedStationConnections.length = 0;
			mapSelectionService.selectedStations.length = 0;
			this.newDirections.length = 0;
			let mapUpdated = false;

			for (let i = 1; i < directions.length; i++) {
				const previousDirection = directions[i - 1];
				const thisDirection = directions[i];

				if (i > 1 && previousDirection.route?.id === thisDirection.route?.id) {
					const lastNewDirection = this.newDirections[this.newDirections.length - 1];
					lastNewDirection.intermediateStations.push(lastNewDirection.endStation);
					lastNewDirection.endStation = thisDirection.targetStation;
					lastNewDirection.endTime = thisDirection.departureTime + thisDirection.travelTime;
				} else {
					const startTime = i === 1 && !thisDirection.route && i + 1 < directions.length ? directions[i + 1].departureTime - thisDirection.travelTime : thisDirection.departureTime;
					this.newDirections.push({
						startStation: previousDirection.targetStation,
						endStation: thisDirection.targetStation,
						intermediateStations: [],
						route: thisDirection.route,
						icon: thisDirection.route ? ROUTE_TYPES[thisDirection.route.type].icon : "",
						startTime,
						endTime: startTime + thisDirection.travelTime,
						distance: previousDirection.targetStation.getDistance(thisDirection.targetStation),
					});
				}

				const reverse = previousDirection.targetStation.id > thisDirection.targetStation.id;
				const newStationId1 = reverse ? thisDirection.targetStation.id : previousDirection.targetStation.id;
				const newStationId2 = reverse ? previousDirection.targetStation.id : thisDirection.targetStation.id;

				if (thisDirection.route) {
					mapSelectionService.selectedStationConnections.push({stationIds: [newStationId1, newStationId2], routeColor: thisDirection.route.color});
					if (mapDataService.routeTypeVisibility[thisDirection.route.type] === "HIDDEN") {
						mapDataService.routeTypeVisibility[thisDirection.route.type] = "SOLID";
						mapUpdated = true;
					}
				}
				if (i === 1) {
					mapSelectionService.selectedStations.push(previousDirection.targetStation.id);
				}
				mapSelectionService.selectedStations.push(thisDirection.targetStation.id);
			}

			if (mapUpdated) {
				mapDataService.updateData();
			}

			mapSelectionService.select("directions");
		}, REFRESH_INTERVAL, dimensionService);
	}

	public selectStations(startStationId: string, endStationId: string, maxWalkingDistanceString: string) {
		const key = `${startStationId}_${endStationId}_${maxWalkingDistanceString}`;
		this.select(key);
		this.fetchData(key);
	}

	public getDirections() {
		return this.newDirections;
	}

	// Actual pathfinding logic
	private findDirections(
		startStation: Station,
		endStation: Station,
		directionsCompleted: EventEmitter<{ route?: Route, targetStation: Station, departureTime: number, travelTime: number }[]>,
		walkingCache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } },
		directions: { route?: Route, targetStation: Station, departureTime: number, travelTime: number }[],
		globalStationTimes: { [stationId: string]: number },
		startTime: number,
		iterations: number,
	) {
		const tempDirections: { route?: Route, targetStation: Station, departureTime: number, travelTime: number }[] = [{targetStation: startStation, departureTime: startTime, travelTime: 0}];
		const localStationTimes: { [stationId: string]: number } = {};
		localStationTimes[startStation.id] = 0;

		const findDirectionsSegment = (innerIterations: number) => {
			const startLoopTime = Date.now();

			while (true) {
				const lastDirection = tempDirections[tempDirections.length - 1];
				const currentStation = lastDirection.targetStation;

				if (currentStation.id === endStation.id) {
					this.setTimeout(() => this.findDirections(startStation, endStation, directionsCompleted, walkingCache, tempDirections, globalStationTimes, startTime, iterations + 1));
					break;
				}

				const currentTime = lastDirection.departureTime + lastDirection.travelTime;
				const currentDistance = currentStation.getDistance(endStation);
				const bestData: { increase: number, connection?: { route?: Route, targetStation: Station, departureTime: number, travelTime: number } } = {increase: -Infinity};

				const findConnections = (cache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } }) => {
					Object.values(cache[currentStation.id] ?? {}).forEach(({targetStation, connections}) => Object.values(connections).forEach(connection => {
						if (currentStation.id === startStation.id || connection.route || lastDirection.route) {
							const newDepartureTime = connection.route ? DirectionsService.getBestDepartureTime(connection.departureTimes, currentTime) : currentTime;
							if (newDepartureTime >= 0) {
								const arrivalTime = newDepartureTime + connection.travelTime;
								if (arrivalTime <= (localStationTimes[targetStation.id] ?? Infinity) && arrivalTime <= (globalStationTimes[targetStation.id] ?? Infinity)) {
									const increase = (currentDistance - targetStation.getDistance(endStation)) / (newDepartureTime - currentTime + connection.travelTime) - (connection.route ? 0 : 1000000000);
									if (increase > bestData.increase) {
										bestData.increase = increase;
										bestData.connection = {route: connection.route, targetStation, departureTime: newDepartureTime, travelTime: connection.travelTime};
										globalStationTimes[targetStation.id] = arrivalTime - (targetStation.id === endStation.id ? 1000 : 0);
										localStationTimes[targetStation.id] = arrivalTime - 1000;
									} else {
										localStationTimes[targetStation.id] = arrivalTime;
									}
								}
							}
						}
					}));
				};

				findConnections(this.departuresService.getDirectionsCache());
				findConnections(walkingCache);

				if (bestData.connection) {
					tempDirections.push(bestData.connection);
				} else {
					tempDirections.pop();
					if (tempDirections.length === 0) {
						console.debug(`Directions found after ${iterations} iteration(s) and ${innerIterations} inner iteration(s) in ${Date.now() - startTime} ms`);
						directionsCompleted.emit(directions);
						break;
					}
				}

				if (Date.now() - startLoopTime >= 10) {
					this.setTimeout(() => findDirectionsSegment(innerIterations + 1));
					break;
				}
			}
		};

		findDirectionsSegment(0);
	}

	private setTimeout(callback: () => void) {
		clearTimeout(this.directionsTimeoutId);
		this.directionsTimeoutId = setTimeout(() => callback(), 0) as unknown as number;
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
