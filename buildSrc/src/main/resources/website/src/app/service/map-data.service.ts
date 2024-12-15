import {arrayAverage, getFromArray, pushIfNotExists, setIfUndefined} from "../data/utilities";
import {ROUTE_TYPES} from "../data/routeType";
import {LineConnection} from "../entity/lineConnection";
import {StationConnection} from "../entity/stationConnection";
import {EventEmitter, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DimensionService} from "./dimension.service";
import {StationsAndRoutesDTO} from "../entity/generated/stationsAndRoutes";
import {DataServiceBase} from "./data-service-base";
import {Route, RoutePlatform} from "../entity/route";
import {StationDTO} from "../entity/generated/station";
import {Station} from "../entity/station";
import {RouteDTO} from "../entity/generated/route";
import {StationForMap} from "../entity/stationForMap";

const REFRESH_INTERVAL = 30000;

@Injectable({providedIn: "root"})
export class MapDataService extends DataServiceBase<{ data: StationsAndRoutesDTO }> {
	public readonly routes: Route[] = [];
	public readonly stations: Station[] = [];
	public readonly routeTypeVisibility: { [key: string]: "HIDDEN" | "SOLID" | "HOLLOW" } = {};
	public readonly stationConnections: StationConnection[] = [];
	public readonly lineConnections: LineConnection[] = [];
	public readonly stationsForMap: StationForMap[] = [];
	private centerX = 0;
	private centerY = 0;

	public readonly mapLoading = new EventEmitter<void>();
	public readonly drawMap = new EventEmitter<void>();
	public readonly animateMap = new EventEmitter<{ x: number, z: number }>();

	constructor(private readonly httpClient: HttpClient, dimensionService: DimensionService) {
		super(() => this.httpClient.get<{ data: StationsAndRoutesDTO }>(this.getUrl("stations-and-routes")), ({data}) => {
			this.routes.length = 0;
			this.stations.length = 0;

			// Write routes
			const routeIdMap: { [key: string]: { routeDTO: RouteDTO, route: Route } } = {};
			const stationIdToPosition: { [key: string]: { x: number[], y: number[], z: number[] } } = {};
			const availableRouteTypes: string[] = [];
			data.routes.forEach(routeDTO => {
				if (routeDTO.stations.length > 1 && !routeDTO.hidden) {
					const route = new Route(routeDTO);
					this.routes.push(route);
					routeIdMap[routeDTO.id] = {routeDTO, route};
					routeDTO.stations.forEach(({id, x, y, z}) => {
						setIfUndefined(stationIdToPosition, id, () => ({x: [], y: [], z: []}));
						stationIdToPosition[id].x.push(x);
						stationIdToPosition[id].y.push(y);
						stationIdToPosition[id].z.push(z);
					});
					pushIfNotExists(availableRouteTypes, routeDTO.type);
				}
			});

			// Write stations
			const stationIdMap: { [key: string]: { stationDTO: StationDTO, station: Station } } = {};
			data.stations.forEach(stationDTO => getFromArray(stationIdToPosition, stationDTO.id, position => {
				const station = new Station(stationDTO, arrayAverage(position.x), arrayAverage(position.y), arrayAverage(position.z));
				this.stations.push(station);
				stationIdMap[stationDTO.id] = {stationDTO, station};
			}));

			// Write station connection cache
			this.stations.forEach(station => getFromArray(stationIdMap, station.id, stationCache => stationCache.stationDTO.connections.forEach(connection => getFromArray(stationIdMap, connection, stationCacheConnection => station.connections.push(stationCacheConnection.station)))));

			// Write route platform cache and station route cache
			this.routes.forEach(route => getFromArray(routeIdMap, route.id, routeCache => {
				for (let i = 0; i < routeCache.routeDTO.stations.length; i++) {
					const routeStationDTO = routeCache.routeDTO.stations[i];
					getFromArray(stationIdMap, routeStationDTO.id, stationCache => {
						stationCache.station.routes.push(route);
						route.routePlatforms.push(new RoutePlatform(routeStationDTO, stationCache.station, i === routeCache.routeDTO.stations.length - 1 ? 0 : routeCache.routeDTO.durations[i]));
					});
				}
			}));

			// Update route type visibility
			Object.keys(ROUTE_TYPES).forEach(routeType => {
				if (availableRouteTypes.includes(routeType)) {
					setIfUndefined(this.routeTypeVisibility, routeType, () => "HIDDEN");
				} else {
					delete this.routeTypeVisibility[routeType];
				}
			});

			if (availableRouteTypes.length > 0 && Object.values(this.routeTypeVisibility).every(visibility => visibility === "HIDDEN")) {
				this.routeTypeVisibility[Object.keys(this.routeTypeVisibility)[0]] = "SOLID";
			}

			this.dimensionService.setDimensions(data.dimensions);
			this.updateData();
		}, REFRESH_INTERVAL, dimensionService);
		this.fetchData("");
	}

	public setDimension(dimension: string) {
		this.mapLoading.emit();
		this.dimensionService.setDimension(dimension);
		this.fetchData("");
	}

	public getCenterX() {
		return this.centerX;
	}

	public getCenterY() {
		return this.centerY;
	}

	public updateData() {
		this.stationsForMap.length = 0;
		this.lineConnections.length = 0;
		this.stationConnections.length = 0;

		const lineConnectionsOneWay: { [key: string]: { forwards: boolean, backwards: boolean } } = {};
		const stationRoutes: { [key: string]: { [key: string]: number[] } } = {};
		const stationGroups: { [key: string]: { [key: string]: string[] } } = {};
		const getKeyAndReverseFromStationIds = (stationId1: string, stationId2: string): [string, boolean] => {
			const reverse = stationId1 > stationId2;
			return [`${reverse ? stationId2 : stationId1}_${reverse ? stationId1 : stationId2}`, reverse];
		};

		this.routes.forEach(({routePlatforms, color, type}) => {
			if (this.routeTypeVisibility[type] !== "HIDDEN") {
				const iterateStations = (iterateForwards: boolean) => {
					for (let i = 0; i < routePlatforms.length; i++) {
						const index = iterateForwards ? i : routePlatforms.length - i - 1;
						const currentTempStation = routePlatforms[index]?.station;
						const currentTempStationId = currentTempStation.id;
						const previousTempStation = routePlatforms[index + (iterateForwards ? -1 : 1)]?.station;
						const nextTempStation = routePlatforms[index + (iterateForwards ? 1 : -1)]?.station;
						const tempStation1 = previousTempStation === undefined ? currentTempStation : previousTempStation;
						const tempStation2 = nextTempStation === undefined ? currentTempStation : nextTempStation;
						const colorAndType = `${color}|${type}`;
						setIfUndefined(stationRoutes, currentTempStationId, () => ({}));
						setIfUndefined(stationRoutes[currentTempStationId], colorAndType, () => []);
						stationRoutes[currentTempStationId][colorAndType].push((Math.round(Math.atan2(tempStation2.x - tempStation1.x, tempStation2.z - tempStation1.z) * 4 / Math.PI) + 8) % 4);

						if (nextTempStation !== undefined) {
							const nextStationId = tempStation2.id;
							if (nextStationId !== currentTempStationId) {
								setIfUndefined(stationGroups, currentTempStationId, () => ({}));
								setIfUndefined(stationGroups[currentTempStationId], nextStationId, () => []);
								pushIfNotExists(stationGroups[currentTempStationId][nextStationId], colorAndType);
								if (iterateForwards) {
									const [key, reverse] = getKeyAndReverseFromStationIds(currentTempStationId, nextStationId);
									const newKey = `${key}_${colorAndType}`;
									setIfUndefined(lineConnectionsOneWay, newKey, () => ({forwards: false, backwards: false}));
									lineConnectionsOneWay[newKey][reverse ? "backwards" : "forwards"] = true;
								}
							}
						}
					}
				};

				iterateStations(true);
				iterateStations(false);
			}
		});

		const lineConnections: { [key: string]: { lineConnectionParts: { color: string, oneWay: number, offset1: number, offset2: number }[], direction1: 0 | 1 | 2 | 3, direction2: 0 | 1 | 2 | 3, x1: number | undefined, x2: number | undefined, z1: number | undefined, z2: number | undefined, stationId1: string, stationId2: string, length: number } } = {};
		const stationConnections: { [key: string]: { x1: number | undefined, x2: number | undefined, z1: number | undefined, z2: number | undefined, sizeRatio: number, start45: boolean } } = {};
		let closestDistance: number;
		let maxLineConnectionLength = 1;

		this.stations.forEach(station => {
			if (station.id in stationRoutes) {
				const combinedGroups: string[][] = [];
				const stationDirection: { [key: string]: 0 | 1 | 2 | 3 } = {};
				setIfUndefined(stationGroups, station.id, () => ({}));

				Object.values(stationGroups[station.id]).forEach(routeColors1 => {
					const combinedGroup = [...routeColors1];
					Object.values(stationGroups[station.id]).forEach(routeColors2 => {
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

				const testRouteColors: string[] = [];
				combinedGroups.forEach(combinedGroup => combinedGroup.forEach(routeColor => {
					console.assert(!testRouteColors.includes(routeColor), "Duplicate colors in combined groups", combinedGroups);
					testRouteColors.push(routeColor);
				}));

				const routesForDirection: [string[], string[], string[], string[]] = [[], [], [], []];
				combinedGroups.forEach(combinedGroup => {
					const directionsCount = [0, 0, 0, 0];
					combinedGroup.forEach(routeColor => stationRoutes[station.id][routeColor].forEach(direction => directionsCount[direction]++));
					let direction: 0 | 1 | 2 | 3 = 0;
					let max = 0;

					for (let i = 0; i < 4; i++) {
						if (directionsCount[i] > max) {
							direction = i as 0 | 1 | 2 | 3;
							max = directionsCount[i];
						}
					}

					combinedGroup.forEach(routeColor => {
						routesForDirection[direction].push(routeColor);
						stationDirection[routeColor] = direction;
					});
				});

				const rotate = routesForDirection[1].length + routesForDirection[3].length > routesForDirection[0].length + routesForDirection[2].length;
				const width = Math.max(Math.max(0, routesForDirection[rotate ? 1 : 0].length - 1), Math.max(0, routesForDirection[rotate ? 0 : 1].length - 1) * Math.SQRT1_2);
				const height = Math.max(Math.max(0, routesForDirection[rotate ? 3 : 2].length - 1), Math.max(0, routesForDirection[rotate ? 2 : 3].length - 1) * Math.SQRT1_2);
				this.stationsForMap.push({station, rotate: rotate, routeCount: Object.keys(stationRoutes[station.id]).length, width, height});
				routesForDirection.forEach(routesForOneDirection => routesForOneDirection.sort());

				Object.entries(stationGroups[station.id]).forEach(groupEntry => {
					const [stationId, routeColors] = groupEntry;
					const [key, reverse] = getKeyAndReverseFromStationIds(station.id, stationId);
					routeColors.sort();
					setIfUndefined(lineConnections, key, () => ({
						lineConnectionParts: routeColors.map(() => Object.create(null)),
						direction1: 0 as 0,
						direction2: 0 as 0,
						stationId1: "",
						stationId2: "",
						x1: undefined, x2: undefined,
						z1: undefined, z2: undefined,
						length: 0,
					}));
					const index = reverse ? 2 : 1;
					const direction = stationDirection[routeColors[0]];
					const lineConnection = lineConnections[key];
					lineConnection[`direction${index}`] = direction;
					lineConnection[`stationId${index}`] = station.id;
					lineConnection[`x${index}`] = station.x;
					lineConnection[`z${index}`] = station.z;

					for (let i = 0; i < routeColors.length; i++) {
						const color = routeColors[i];
						const lineConnectionDetails = lineConnection.lineConnectionParts[i];
						console.assert(lineConnectionDetails.color === undefined || lineConnectionDetails.color === color, "Color and offsets mismatch", lineConnectionDetails);
						lineConnectionDetails.color = color;
						lineConnectionDetails[`offset${index}`] = routesForDirection[direction].indexOf(color) - routesForDirection[direction].length / 2 + 0.5;
						const {forwards, backwards} = lineConnectionsOneWay[`${key}_${color}`];
						lineConnectionDetails.oneWay = forwards && backwards ? 0 : forwards ? 1 : -1;
					}

					if (lineConnection.x1 != undefined && lineConnection.z1 != undefined && lineConnection.x2 != undefined && lineConnection.z2 != undefined) {
						const lineConnectionLength = Math.abs(lineConnection.x2 - lineConnection.x1) + Math.abs(lineConnection.z2 - lineConnection.z1);
						lineConnection.length = lineConnectionLength;
						maxLineConnectionLength = Math.max(maxLineConnectionLength, lineConnectionLength);
					}
				});

				const distance = Math.abs(station.x) + Math.abs(station.z);
				if (closestDistance === undefined || distance < closestDistance) {
					closestDistance = distance;
					this.centerX = -station.x;
					this.centerY = -station.z;
				}

				station.connections.forEach(connectingStation => {
					const [key, reverse] = getKeyAndReverseFromStationIds(station.id, connectingStation.id);
					setIfUndefined(stationConnections, key, () => ({x1: undefined, x2: undefined, z1: undefined, z2: undefined, sizeRatio: 0, start45: false}));
					stationConnections[key][`x${reverse ? 2 : 1}`] = station.x;
					stationConnections[key][`z${reverse ? 2 : 1}`] = station.z;
					const sizeRatio = (Math.max(width, height) + 1) / (Math.min(width, height) + 1);
					if (sizeRatio > stationConnections[key].sizeRatio) {
						stationConnections[key].sizeRatio = sizeRatio;
						stationConnections[key].start45 = reverse !== rotate;
					}
				});
			}
		});

		this.stationsForMap.sort((stationForMap1, stationForMap2) => {
			if (stationForMap1.routeCount === stationForMap2.routeCount) {
				return stationForMap2.station.name.length - stationForMap1.station.name.length;
			} else {
				return stationForMap2.routeCount - stationForMap1.routeCount;
			}
		});

		Object.values(lineConnections).sort((lineConnection1, lineConnection2) => lineConnection2.length - lineConnection1.length).forEach(lineConnection => {
			if (lineConnection.x1 !== undefined && lineConnection.x2 !== undefined && lineConnection.z1 !== undefined && lineConnection.z2 !== undefined) {
				this.lineConnections.push({
					lineConnectionParts: lineConnection.lineConnectionParts,
					direction1: lineConnection.direction1, direction2: lineConnection.direction2,
					x1: lineConnection.x1, x2: lineConnection.x2,
					z1: lineConnection.z1, z2: lineConnection.z2,
					stationId1: lineConnection.stationId1,
					stationId2: lineConnection.stationId2,
					length: lineConnection.length,
					relativeLength: lineConnection.length / (maxLineConnectionLength + 1),
				});
			}
		});

		Object.values(stationConnections).forEach(stationConnection => {
			if (stationConnection.x1 != undefined && stationConnection.x2 != undefined && stationConnection.z1 != undefined && stationConnection.z2 != undefined) {
				this.stationConnections.push({
					x1: stationConnection.x1,
					x2: stationConnection.x2,
					z1: stationConnection.z1,
					z2: stationConnection.z2,
					start45: stationConnection.start45,
				});
			}
		});

		this.drawMap.emit();
	}
}
