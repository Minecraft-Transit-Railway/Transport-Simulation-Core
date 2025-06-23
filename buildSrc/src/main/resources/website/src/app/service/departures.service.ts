import {EventEmitter, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DimensionService} from "./dimension.service";
import {setIfUndefined} from "../data/utilities";
import {DataServiceBase} from "./data-service-base";
import {Route, RoutePlatform} from "../entity/route";
import {MapDataService} from "./map-data.service";
import {Station} from "../entity/station";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class DeparturesService extends DataServiceBase<{
	departures: Record<string, { departureFromNow: number, deviation: number }[]>,
	directionsCache: Record<string, Record<string, { targetPosition: { x: number, y: number, z: number }, connections: Record<string, { route?: Route, departureTimes: number[], travelTime: number }> }>>,
	routePlatformsCache1: Record<string, { station: Station, routePlatforms: Record<string, { x: number, y: number, z: number, name: string }> }>,
	routePlatformsCache2: Record<string, RoutePlatform>,
	lastUpdated: number,
}> {
	private departures: Record<string, { departureFromNow: number, deviation: number }[]> = {};
	private directionsCache: Record<string, Record<string, { targetPosition: { x: number, y: number, z: number }, connections: Record<string, { route?: Route, departureTimes: number[], travelTime: number }> }>> = {};
	private routePlatformsCache1: Record<string, { station: Station, routePlatforms: Record<string, { x: number, y: number, z: number, name: string }> }> = {};
	private routePlatformsCache2: Record<string, RoutePlatform> = {};
	private lastUpdated = 0;

	constructor(private readonly httpClient: HttpClient, mapDataService: MapDataService, dimensionService: DimensionService) {
		super(() => {
			const cacheCompleted = new EventEmitter<{
				departures: Record<string, { departureFromNow: number, deviation: number }[]>,
				directionsCache: Record<string, Record<string, { targetPosition: { x: number, y: number, z: number }, connections: Record<string, { route?: Route, departureTimes: number[], travelTime: number }> }>>,
				routePlatformsCache1: Record<string, { station: Station, routePlatforms: Record<string, { x: number, y: number, z: number, name: string }> }>,
				routePlatformsCache2: Record<string, RoutePlatform>,
				lastUpdated: number,
			}>();
			this.httpClient.get<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }>(this.getUrl("departures")).subscribe(data => {
				const createCache = (
					index: number,
					iterations: number,
					mappedRoutes: number,
					startTime: number,
					departures: Record<string, { departureFromNow: number, deviation: number }[]>,
					directionsCache: Record<string, Record<string, { targetPosition: { x: number, y: number, z: number }, connections: Record<string, { route?: Route, departureTimes: number[], travelTime: number }> }>>,
					routePlatformsCache1: Record<string, { station: Station, routePlatforms: Record<string, { x: number, y: number, z: number, name: string }> }>,
					routePlatformsCache2: Record<string, RoutePlatform>,
				) => {
					const startLoopTime = Date.now();

					while (true) {
						if (index >= data.data.departures.length) {
							if (index > 0 && mappedRoutes === 0) {
								console.warn("Encountered invalid routes while creating directions cache!");
								cacheCompleted.emit({departures: this.departures, directionsCache: this.directionsCache, routePlatformsCache1: this.routePlatformsCache1, routePlatformsCache2: this.routePlatformsCache2, lastUpdated: this.lastUpdated});
							} else {
								console.debug(`Cache for ${index} route(s) created after ${iterations} iteration(s) in ${Date.now() - startTime} ms`);
								cacheCompleted.emit({departures, directionsCache, routePlatformsCache1, routePlatformsCache2, lastUpdated: startTime});
							}
							break;
						}

						const routeDepartures = data.data.departures[index];
						const departuresFromNow: number[] = [];

						// Cache for showing live vehicle positions
						routeDepartures.departures.forEach(departuresForRoute => departuresForRoute.departures.forEach(departure => {
							setIfUndefined(departures, routeDepartures.id, () => []);
							const departureFromNow = departure + departuresForRoute.deviation + data.data.cachedResponseTime - data.currentTime;
							departures[routeDepartures.id].push({departureFromNow, deviation: departuresForRoute.deviation});
							departuresFromNow.push(departureFromNow);
						}));

						departuresFromNow.sort((a, b) => a - b);
						const route = mapDataService.routes.find(route => route.id === routeDepartures.id);

						// Cache for finding directions
						if (route) {
							const routePlatforms = route.routePlatforms;
							let timeOffset = routePlatforms[routePlatforms.length - 1].dwellTime;
							for (let i = routePlatforms.length - 2; i >= 0; i--) {
								const thisRoutePlatform = routePlatforms[i];
								const nextRoutePlatform = routePlatforms[i + 1];
								const thisRoutePlatformKey = DeparturesService.getPositionKey(thisRoutePlatform);
								const nextRoutePlatformKey = DeparturesService.getPositionKey(nextRoutePlatform);
								timeOffset += thisRoutePlatform.duration;

								setIfUndefined(directionsCache, thisRoutePlatformKey, () => ({}));
								setIfUndefined(directionsCache[thisRoutePlatformKey], nextRoutePlatformKey, () => ({targetPosition: nextRoutePlatform, targetPlatformName: nextRoutePlatform.name, connections: {}}));
								directionsCache[thisRoutePlatformKey][nextRoutePlatformKey].connections[route.id] = {route, departureTimes: [], travelTime: thisRoutePlatform.duration};
								departuresFromNow.forEach(departureFromNow => {
									const departureOffset = departureFromNow - timeOffset;
									if (departureOffset >= 0) {
										directionsCache[thisRoutePlatformKey][nextRoutePlatformKey].connections[route.id].departureTimes.push(departureOffset + startTime);
									}
								});

								timeOffset += thisRoutePlatform.dwellTime;

								setIfUndefined(routePlatformsCache1, nextRoutePlatform.station.id, () => ({station: nextRoutePlatform.station, routePlatforms: {}}));
								routePlatformsCache1[nextRoutePlatform.station.id].routePlatforms[nextRoutePlatformKey] = nextRoutePlatform;
								routePlatformsCache2[nextRoutePlatformKey] = nextRoutePlatform;

								if (i === 0) {
									setIfUndefined(routePlatformsCache1, thisRoutePlatform.station.id, () => ({station: thisRoutePlatform.station, routePlatforms: {}}));
									routePlatformsCache1[thisRoutePlatform.station.id].routePlatforms[thisRoutePlatformKey] = thisRoutePlatform;
									routePlatformsCache2[thisRoutePlatformKey] = thisRoutePlatform;
								}
							}
							mappedRoutes++;
						}

						index++;

						if (Date.now() - startLoopTime >= 10) {
							setTimeout(() => createCache(index, iterations + 1, mappedRoutes, startTime, departures, directionsCache, routePlatformsCache1, routePlatformsCache2), 0);
							break;
						}
					}
				};

				createCache(0, 0, 0, Date.now(), {}, {}, {}, {});
			});
			return cacheCompleted;
		}, data => {
			this.departures = data.departures;
			this.directionsCache = data.directionsCache;
			this.routePlatformsCache1 = data.routePlatformsCache1;
			this.routePlatformsCache2 = data.routePlatformsCache2;
			this.lastUpdated = data.lastUpdated;
		}, REFRESH_INTERVAL, dimensionService);
		this.fetchData("");
	}

	public getDepartures(routeId: string, predicate: (departure: { departureFromNow: number, deviation: number }) => boolean) {
		const departuresForRoute = this.departures[routeId];
		if (departuresForRoute) {
			const newDepartures: { departureFromNow: number, deviation: number }[] = [];
			departuresForRoute.forEach(({departureFromNow, deviation}) => {
				const newDeparture = {departureFromNow: departureFromNow + this.lastUpdated - Date.now(), deviation};
				if (predicate(newDeparture)) {
					newDepartures.push(newDeparture);
				}
			});
			newDepartures.sort((departure1, departure2) => departure1.departureFromNow - departure2.departureFromNow);
			return newDepartures;
		} else {
			return [];
		}
	}

	public getDirectionsCache() {
		return this.directionsCache;
	}

	public getRoutePlatformsCache1() {
		return this.routePlatformsCache1;
	}

	public getRoutePlatformsCache2() {
		return this.routePlatformsCache2;
	}

	public static getDistance(position1: { x: number, y: number, z: number }, position2: { x: number, y: number, z: number }) {
		return Math.abs(position1.x - position2.x) + Math.abs(position1.y - position2.y) + Math.abs(position1.z - position2.z);
	}

	public static getPositionKey(position: { x: number, y: number, z: number }) {
		return Math.round(position.x) + "_" + Math.round(position.y) + "_" + Math.round(position.z);
	}
}

class DataResponse {
	readonly id: string = "";
	readonly departures: DataResponseDeparturesForRoute[] = [];
}

class DataResponseDeparturesForRoute {
	readonly deviation: number = 0;
	readonly departures: number[] = [];
}
