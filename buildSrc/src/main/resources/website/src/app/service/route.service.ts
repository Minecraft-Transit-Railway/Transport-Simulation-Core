import {Injectable} from "@angular/core";
import {MapDataService} from "./map-data.service";
import {SimplifyRoutesPipe} from "../pipe/simplifyRoutesPipe";
import {DeparturesService} from "./departures.service";
import {Route} from "../entity/route";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {DimensionService} from "./dimension.service";

@Injectable({providedIn: "root"})
export class RouteVariationService extends SelectableDataServiceBase<void, Route> {
	public readonly routeStationDetails: { id: string, name: string, duration: number, durationSeconds: number, dwellTime: number, dwellTimeSeconds: number, vehicles: { deviation: number, percentage: number }[] }[] = [];
	private totalDuration = 0;

	constructor(private readonly routeKeyService: RouteKeyService, departuresService: DeparturesService, dimensionService: DimensionService) {
		super(routeId => {
			this.routeStationDetails.length = 0;
			this.totalDuration = 0;
			const selectedRoutes = this.routeKeyService.getSelectedData();
			const selectedRoute = selectedRoutes ? selectedRoutes.find(route => route.id === routeId) ?? selectedRoutes[0] : undefined;
			if (selectedRoute) {
				for (let i = 0; i < selectedRoute.routePlatforms.length; i++) {
					const {station, dwellTime, duration} = selectedRoute.routePlatforms[i];
					this.routeStationDetails.push({id: station.id, name: station.name, duration, durationSeconds: Math.round(duration / 1000), dwellTime, dwellTimeSeconds: Math.round(dwellTime / 1000), vehicles: []});
					this.totalDuration += dwellTime;
					if (i < selectedRoute.routePlatforms.length - 1) {
						this.totalDuration += duration;
					}
				}
			}
			updateDepartures(selectedRoute);
			return selectedRoute;
		}, () => {
		}, () => {
		}, () => {
		}, 0, dimensionService);

		const departures: { departureFromNow: number, deviation: number }[] = [];
		const updateDepartures = (route: Route | undefined) => {
			departures.length = 0;
			if (route) {
				departuresService.getDepartures(route.id, ({departureFromNow}) => departureFromNow <= this.totalDuration).forEach(departure => departures.push(departure));
			}
		};

		routeKeyService.selectionChanged.subscribe(() => this.select(""));
		departuresService.dataProcessed.subscribe(() => updateDepartures(this.getSelectedData()));

		setInterval(() => {
			this.routeStationDetails.forEach(({vehicles}) => vehicles.length = 0);
			let routeStationIndex = this.routeStationDetails.length - 1;
			let departureIndex = 0;
			let cumulativeTime = 0;

			while (routeStationIndex >= 0 && departureIndex < departures.length) {
				cumulativeTime += this.routeStationDetails[routeStationIndex].dwellTime;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					this.routeStationDetails[routeStationIndex].vehicles.push({deviation: departures[departureIndex].deviation, percentage: 0});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}

				routeStationIndex--;
				if (routeStationIndex < 0) {
					break;
				}

				const halfDuration = this.routeStationDetails[routeStationIndex].duration / 2;
				cumulativeTime += halfDuration;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					this.routeStationDetails[routeStationIndex + 1].vehicles.push({deviation: departures[departureIndex].deviation, percentage: -difference / halfDuration});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}

				cumulativeTime += halfDuration;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					this.routeStationDetails[routeStationIndex].vehicles.push({deviation: departures[departureIndex].deviation, percentage: 1 - difference / halfDuration});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}
			}
		}, 100);
	}

	public getNames() {
		return this.routeKeyService.getSelectedData()?.map(route => route.name) ?? [];
	}

	public getTotalDurationSeconds() {
		return Math.round(this.totalDuration / 1000);
	}
}

@Injectable({providedIn: "root"})
export class RouteKeyService extends SelectableDataServiceBase<void, Route[]> {

	constructor(private readonly dataService: MapDataService, dimensionService: DimensionService) {
		super(routeKey => {
			const selectedRouteVariations: Route[] = [];
			this.dataService.routes.forEach(route => {
				if (SimplifyRoutesPipe.getRouteKey(route) === routeKey) {
					selectedRouteVariations.push(route);
				}
			});
			SimplifyRoutesPipe.sortRoutes(selectedRouteVariations);
			return selectedRouteVariations;
		}, () => {
		}, () => {
		}, () => {
		}, 0, dimensionService);
	}
}
