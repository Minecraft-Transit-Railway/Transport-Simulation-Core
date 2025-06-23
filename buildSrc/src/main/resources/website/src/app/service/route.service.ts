import {Injectable} from "@angular/core";
import {MapDataService} from "./map-data.service";
import {SimplifyRoutesPipe} from "../pipe/simplifyRoutesPipe";
import {DeparturesService} from "./departures.service";
import {Route} from "../entity/route";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {DimensionService} from "./dimension.service";
import {pushIfNotExists} from "../data/utilities";
import {MapSelectionService} from "./map-selection.service";

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
					this.routeStationDetails[routeStationIndex + 1].vehicles.push({deviation: departures[departureIndex].deviation, percentage: Math.max(0, Math.min(1, -difference / halfDuration))});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}

				cumulativeTime += halfDuration;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					this.routeStationDetails[routeStationIndex].vehicles.push({deviation: departures[departureIndex].deviation, percentage: Math.max(0, Math.min(1, 1 - difference / halfDuration))});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}
			}
		}, 100);
	}

	public getTotalDurationSeconds() {
		return Math.round(this.totalDuration / 1000);
	}
}

@Injectable({providedIn: "root"})
export class RouteKeyService extends SelectableDataServiceBase<void, Route[]> {

	constructor(mapDataService: MapDataService, mapSelectionService: MapSelectionService, dimensionService: DimensionService) {
		super(routeKey => {
			mapSelectionService.selectedStationConnections.length = 0;
			mapSelectionService.selectedStations.length = 0;
			const selectedRouteVariations: Route[] = [];
			const tempStationConnections: Record<string, { stationIds: [string, string], routeColor: number }> = {};
			let mapUpdated = false;

			mapDataService.routes.forEach(route => {
				if (SimplifyRoutesPipe.getRouteKey(route) === routeKey) {
					selectedRouteVariations.push(route);

					// Update list of selected stations
					for (let i = 0; i < route.routePlatforms.length - 1; i++) {
						const stationId1 = route.routePlatforms[i].station.id;
						const stationId2 = route.routePlatforms[i + 1].station.id;
						const reverse = stationId1 > stationId2;
						const newStationId1 = reverse ? stationId2 : stationId1;
						const newStationId2 = reverse ? stationId1 : stationId2;
						tempStationConnections[`${newStationId1}_${newStationId2}`] = {stationIds: [newStationId1, newStationId2], routeColor: route.color};
						pushIfNotExists(mapSelectionService.selectedStations, stationId1);
						pushIfNotExists(mapSelectionService.selectedStations, stationId2);
					}

					// Update map visibility
					if (mapDataService.routeTypeVisibility[route.type] === "HIDDEN") {
						mapDataService.routeTypeVisibility[route.type] = "SOLID";
						mapUpdated = true;
					}
				}
			});

			if (mapUpdated) {
				mapDataService.updateData();
			}

			SimplifyRoutesPipe.sortRoutes(selectedRouteVariations);
			Object.values(tempStationConnections).forEach(stationConnection => mapSelectionService.selectedStationConnections.push(stationConnection));
			mapSelectionService.select("route");
			return selectedRouteVariations;
		}, () => mapSelectionService.reset("route"), () => {
		}, () => {
		}, 0, dimensionService);
	}
}
