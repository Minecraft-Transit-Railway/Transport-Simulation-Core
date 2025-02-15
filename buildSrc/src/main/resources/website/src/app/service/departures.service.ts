import {EventEmitter, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DimensionService} from "./dimension.service";
import {setIfUndefined} from "../data/utilities";
import {DataServiceBase} from "./data-service-base";
import {Station} from "../entity/station";
import {Route} from "../entity/route";
import {MapDataService} from "./map-data.service";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class DeparturesService extends DataServiceBase<{
	departures: { [key: string]: { departureFromNow: number, deviation: number }[] },
	directionsCache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } },
	lastUpdated: number,
}> {
	private departures: { [key: string]: { departureFromNow: number, deviation: number }[] } = {};
	private directionsCache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } } = {};
	private lastUpdated = 0;

	constructor(private readonly httpClient: HttpClient, mapDataService: MapDataService, dimensionService: DimensionService) {
		super(() => {
			const cacheCompleted = new EventEmitter<{
				departures: { [key: string]: { departureFromNow: number, deviation: number }[] },
				directionsCache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } },
				lastUpdated: number,
			}>();
			this.httpClient.get<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }>(this.getUrl("departures")).subscribe(data => {
				const createCache = (
					index: number,
					iterations: number,
					mappedRoutes: number,
					startTime: number,
					departures: { [key: string]: { departureFromNow: number, deviation: number }[] },
					directionsCache: { [stationId: string]: { [targetStationId: string]: { targetStation: Station, connections: { [routeId: string]: { route?: Route, departureTimes: number[], travelTime: number } } } } },
				) => {
					const startLoopTime = Date.now();

					while (true) {
						if (index >= data.data.departures.length) {
							if (index > 0 && mappedRoutes === 0) {
								console.warn("Encountered invalid routes while creating directions cache!");
								cacheCompleted.emit({departures: this.departures, directionsCache: this.directionsCache, lastUpdated: this.lastUpdated});
							} else {
								this.departures = departures;
								this.directionsCache = directionsCache;
								this.lastUpdated = startTime;
								console.debug(`Cache for ${index} route(s) created after ${iterations} iteration(s) in ${Date.now() - startTime} ms`);
								cacheCompleted.emit({departures, directionsCache, lastUpdated: startTime});
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
								const thisStation = routePlatforms[i];
								const nextStation = routePlatforms[i + 1];
								timeOffset += thisStation.duration + thisStation.dwellTime;
								setIfUndefined(directionsCache, thisStation.station.id, () => ({}));
								setIfUndefined(directionsCache[thisStation.station.id], nextStation.station.id, () => ({targetStation: nextStation.station, connections: {}}));
								directionsCache[thisStation.station.id][nextStation.station.id].connections[route.id] = {route, departureTimes: [], travelTime: thisStation.duration};
								departuresFromNow.forEach(departureFromNow => {
									const departureOffset = departureFromNow - timeOffset;
									if (departureOffset >= 0) {
										directionsCache[thisStation.station.id][nextStation.station.id].connections[route.id].departureTimes.push(departureOffset + startTime);
									}
								});
							}
							mappedRoutes++;
						}

						index++;

						if (Date.now() - startLoopTime >= 10) {
							setTimeout(() => createCache(index, iterations + 1, mappedRoutes, startTime, departures, directionsCache), 0);
							break;
						}
					}
				};

				createCache(0, 0, 0, Date.now(), {}, {});
			});
			return cacheCompleted;
		}, data => {
			this.departures = data.departures;
			this.directionsCache = data.directionsCache;
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
}

class DataResponse {
	readonly id: string = "";
	readonly departures: DataResponseDeparturesForRoute[] = [];
}

class DataResponseDeparturesForRoute {
	readonly deviation: number = 0;
	readonly departures: number[] = [];
}
