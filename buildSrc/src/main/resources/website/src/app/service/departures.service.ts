import {EventEmitter, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DimensionService} from "./dimension.service";
import {setIfUndefined} from "../data/utilities";
import {DataServiceBase} from "./data-service-base";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class DeparturesService extends DataServiceBase<{
	departures: Record<string, { departureFromNow: number, deviation: number }[]>,
	lastUpdated: number,
}> {
	private departures: Record<string, { departureFromNow: number, deviation: number }[]> = {};
	private lastUpdated = 0;

	constructor(httpClient: HttpClient, dimensionService: DimensionService) {
		super(() => {
			const cacheCompleted = new EventEmitter<{
				departures: Record<string, { departureFromNow: number, deviation: number }[]>,
				lastUpdated: number,
			}>();
			httpClient.get<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }>(this.getUrl("departures")).subscribe(data => {
				const createCache = (
					index: number,
					iterations: number,
					startTime: number,
					departures: Record<string, { departureFromNow: number, deviation: number }[]>,
				) => {
					const startLoopTime = Date.now();

					while (true) {
						if (index >= data.data.departures.length) {
							console.debug(`Cache for ${index} route(s) created after ${iterations} iteration(s) in ${Date.now() - startTime} ms`);
							cacheCompleted.emit({departures, lastUpdated: startTime});
							break;
						}

						const routeDepartures = data.data.departures[index];

						// Cache for showing live vehicle positions
						routeDepartures.departures.forEach(departuresForRoute => departuresForRoute.departures.forEach(departure => {
							setIfUndefined(departures, routeDepartures.id, () => []);
							const departureFromNow = departure + departuresForRoute.deviation + data.data.cachedResponseTime - data.currentTime;
							departures[routeDepartures.id].push({departureFromNow, deviation: departuresForRoute.deviation});
						}));

						index++;

						if (Date.now() - startLoopTime >= 10) {
							setTimeout(() => createCache(index, iterations + 1, startTime, departures), 0);
							break;
						}
					}
				};

				createCache(0, 0, Date.now(), {});
			});
			return cacheCompleted;
		}, data => {
			this.departures = data.departures;
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
