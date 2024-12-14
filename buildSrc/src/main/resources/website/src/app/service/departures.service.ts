import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DimensionService} from "./dimension.service";
import {setIfUndefined} from "../data/utilities";
import {DataServiceBase} from "./data-service-base";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class DeparturesService extends DataServiceBase<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }> {
	private departures: { [key: string]: { departureFromNow: number, deviation: number }[] } = {};
	private lastUpdated = 0;

	constructor(private readonly httpClient: HttpClient, dimensionService: DimensionService) {
		super(() => this.httpClient.get<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }>(this.getUrl("departures")), (data: { currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }) => {
			this.departures = {};
			data.data.departures.forEach(routeDepartures => routeDepartures.departures.forEach(departuresForRoute => departuresForRoute.departures.forEach(departure => {
				setIfUndefined(this.departures, routeDepartures.id, () => []);
				this.departures[routeDepartures.id].push({departureFromNow: departure + departuresForRoute.deviation + data.data.cachedResponseTime - data.currentTime, deviation: departuresForRoute.deviation});
			})));
			this.lastUpdated = Date.now();
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
}

class DataResponse {
	readonly id: string = "";
	readonly departures: DataResponseDeparturesForRoute[] = [];
}

class DataResponseDeparturesForRoute {
	readonly deviation: number = 0;
	readonly departures: number[] = [];
}
