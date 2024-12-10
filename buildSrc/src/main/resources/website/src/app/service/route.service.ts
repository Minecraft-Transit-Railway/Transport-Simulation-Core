import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, RouteExtended} from "./data.service";
import {ServiceBase} from "./service";
import {DimensionService} from "./dimension.service";
import {SimplifyRoutesPipe} from "../pipe/simplifyRoutesPipe";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class RouteService extends ServiceBase<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }> {
	private readonly selectedRoutes: RouteExtended[] = [];
	private selectedRoute?: RouteExtended;
	private readonly routeStationDetails: { id: string, name: string, duration: number, durationSeconds: number, dwellTime: number, dwellTimeSeconds: number, vehicles: { deviation: number, percentage: number }[] }[] = [];
	private readonly departures: { departureFromNow: number, deviation: number }[] = [];
	private totalDuration = 0;
	private randomSeed = 0;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService, dimensionService: DimensionService) {
		super(() => {
			if (this.selectedRoutes.length > 0) {
				return this.httpClient.get<{ currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }>(this.getUrl("all-arrivals"));
			} else {
				return;
			}
		}, REFRESH_INTERVAL, dimensionService);
		setInterval(() => {
			this.routeStationDetails.forEach(({vehicles}) => vehicles.length = 0);
			let routeStationIndex = this.routeStationDetails.length - 1;
			let departureIndex = 0;
			let cumulativeTime = 0;

			while (routeStationIndex >= 0 && departureIndex < this.departures.length) {
				cumulativeTime += this.routeStationDetails[routeStationIndex].dwellTime;
				if (this.departures[departureIndex].departureFromNow <= cumulativeTime) {
					this.routeStationDetails[routeStationIndex].vehicles.push({deviation: this.departures[departureIndex].deviation, percentage: 0});
					this.departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= this.departures.length) {
						break;
					}
				}

				routeStationIndex--;
				if (routeStationIndex < 0) {
					break;
				}

				const halfDuration = this.routeStationDetails[routeStationIndex].duration / 2;
				cumulativeTime += halfDuration;
				if (this.departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = this.departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					this.routeStationDetails[routeStationIndex + 1].vehicles.push({deviation: this.departures[departureIndex].deviation, percentage: -difference / halfDuration});
					this.departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= this.departures.length) {
						break;
					}
				}

				cumulativeTime += halfDuration;
				if (this.departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = this.departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					this.routeStationDetails[routeStationIndex].vehicles.push({deviation: this.departures[departureIndex].deviation, percentage: 1 - difference / halfDuration});
					this.departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= this.departures.length) {
						break;
					}
				}
			}
		}, 100);
	}

	protected override processData(data: { currentTime: number, data: { cachedResponseTime: number, departures: DataResponse[] } }) {
		this.departures.length = 0;
		data.data.departures.forEach(routeDepartures => {
			if (routeDepartures.id === this.selectedRoute?.id) {
				routeDepartures.departures.forEach(departuresForRoute => departuresForRoute.departures.forEach(departure => {
					const departureFromNow = departure + departuresForRoute.deviation + data.data.cachedResponseTime - data.currentTime;
					if (departureFromNow <= this.totalDuration + REFRESH_INTERVAL) {
						this.departures.push({departureFromNow, deviation: departuresForRoute.deviation});
					}
				}));
			}
		});
		this.departures.sort((departure1, departure2) => departure1.departureFromNow - departure2.departureFromNow);
	}

	public setRoute(routeKey: string) {
		this.selectedRoutes.length = 0;
		this.dataService.getAllRoutes().forEach(route => {
			if (SimplifyRoutesPipe.getRouteKey(route) === routeKey) {
				this.selectedRoutes.push(route);
			}
		});
		SimplifyRoutesPipe.sortRoutes(this.selectedRoutes);
		this.randomSeed = Math.random();
		this.selectRoute(0);
	}

	public getNames() {
		return this.selectedRoutes.map(route => route.name);
	}

	public getRandomSeed() {
		return this.randomSeed;
	}

	public getSelectedRoute() {
		return this.selectedRoute;
	}

	public getRouteStationDetails() {
		return this.routeStationDetails;
	}

	public getTotalDurationSeconds() {
		return Math.round(this.totalDuration / 1000);
	}

	public selectRoute(index: number) {
		this.routeStationDetails.length = 0;
		this.totalDuration = 0;
		this.selectedRoute = this.selectedRoutes[index] ?? this.selectedRoutes[0];
		if (this.selectedRoute) {
			for (let i = 0; i < this.selectedRoute.stations.length; i++) {
				const {id, name, dwellTime} = this.selectedRoute.stations[i];
				const duration = this.selectedRoute.durations[i];
				this.routeStationDetails.push({id, name, duration, durationSeconds: Math.round(duration / 1000), dwellTime, dwellTimeSeconds: Math.round(dwellTime / 1000), vehicles: []});
				this.totalDuration += dwellTime;
				if (i < this.selectedRoute.stations.length - 1) {
					this.totalDuration += duration;
				}
			}
		}
		this.getData("");
		this.departures.length = 0;
	}

	public clear() {
		this.selectedRoutes.length = 0;
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
