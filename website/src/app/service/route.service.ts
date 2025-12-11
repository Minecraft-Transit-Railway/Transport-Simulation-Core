import {inject, Injectable, signal} from "@angular/core";
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
	private readonly routeKeyService = inject(RouteKeyService);

	public readonly routeStationDetails = signal<{ id: string, name: string, duration: number, durationSeconds: number, dwellTime: number, dwellTimeSeconds: number }[]>([]);
	public readonly routeVehicles = signal<{ deviation: number, percentage: number } [][]>([]);
	private readonly totalDuration = signal<number>(0);

	constructor() {
		const departuresService = inject(DeparturesService);
		const dimensionService = inject(DimensionService);

		super(routeId => {
			const routeStationDetails: { id: string, name: string, duration: number, durationSeconds: number, dwellTime: number, dwellTimeSeconds: number }[] = [];
			const routeVehicles: { deviation: number, percentage: number } [][] = [];
			let totalDuration = 0;

			const selectedRoutes = this.routeKeyService.selectedData();
			const selectedRoute = selectedRoutes ? selectedRoutes.find(route => route.id === routeId) ?? selectedRoutes[0] : undefined;

			if (selectedRoute) {
				for (let i = 0; i < selectedRoute.routePlatforms.length; i++) {
					const {station, dwellTime, duration} = selectedRoute.routePlatforms[i];
					routeStationDetails.push({id: station.id, name: station.name, duration, durationSeconds: Math.round(duration / 1000), dwellTime, dwellTimeSeconds: Math.round(dwellTime / 1000)});
					routeVehicles.push([]);
					totalDuration += dwellTime;
					if (i < selectedRoute.routePlatforms.length - 1) {
						totalDuration += duration;
					}
				}
			}

			this.routeStationDetails.set(routeStationDetails);
			this.routeVehicles.set(routeVehicles);
			this.totalDuration.set(totalDuration);
			updateDepartures(selectedRoute);
			return selectedRoute;
		}, () => {
			// empty
		}, () => {
			// empty
		}, () => {
			// empty
		}, 0, dimensionService);

		const departures: { departureFromNow: number, deviation: number }[] = [];
		const updateDepartures = (route: Route | undefined) => {
			departures.length = 0;
			if (route) {
				departuresService.getDepartures(route.id, ({departureFromNow}) => departureFromNow <= this.totalDuration()).forEach(departure => departures.push(departure));
			}
		};

		this.routeKeyService.selectionChanged.subscribe(() => this.select(""));
		departuresService.dataProcessed.subscribe(() => updateDepartures(this.selectedData()));

		setInterval(() => {
			const routeVehicles: { deviation: number, percentage: number }[][] = [];
			this.routeVehicles().forEach(() => routeVehicles.push([]));

			let routeStationIndex = this.routeStationDetails().length - 1;
			let departureIndex = 0;
			let cumulativeTime = 0;

			while (routeStationIndex >= 0 && departureIndex < departures.length) {
				cumulativeTime += this.routeStationDetails()[routeStationIndex].dwellTime;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					routeVehicles[routeStationIndex].push({deviation: departures[departureIndex].deviation, percentage: 0});
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

				const halfDuration = this.routeStationDetails()[routeStationIndex].duration / 2;
				cumulativeTime += halfDuration;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					routeVehicles[routeStationIndex + 1].push({deviation: departures[departureIndex].deviation, percentage: Math.max(-1, Math.min(1, -difference / halfDuration))});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}

				cumulativeTime += halfDuration;
				if (departures[departureIndex].departureFromNow <= cumulativeTime) {
					const difference = departures[departureIndex].departureFromNow - cumulativeTime + halfDuration;
					routeVehicles[routeStationIndex].push({deviation: departures[departureIndex].deviation, percentage: Math.max(-1, Math.min(1, 1 - difference / halfDuration))});
					departures[departureIndex].departureFromNow -= 100;
					departureIndex++;
					if (departureIndex >= departures.length) {
						break;
					}
				}
			}

			this.routeVehicles.set(routeVehicles);
		}, 100);
	}

	public getTotalDurationSeconds() {
		return Math.round(this.totalDuration() / 1000);
	}
}

@Injectable({providedIn: "root"})
export class RouteKeyService extends SelectableDataServiceBase<void, Route[]> {

	constructor() {
		const mapDataService = inject(MapDataService);
		const mapSelectionService = inject(MapSelectionService);
		const dimensionService = inject(DimensionService);

		super(routeKey => {
			mapSelectionService.selectedStationConnections.length = 0;
			mapSelectionService.selectedStations.length = 0;
			const selectedRouteVariations: Route[] = [];
			const tempStationConnections: Record<string, { stationIds: [string, string], routeColor: number }> = {};
			const routeTypeVisibility = mapDataService.routeTypeVisibility();
			let mapUpdated = false;

			mapDataService.routes().forEach(route => {
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
					if (routeTypeVisibility[route.type] === "HIDDEN") {
						routeTypeVisibility[route.type] = "SOLID";
						mapUpdated = true;
					}
				}
			});

			if (mapUpdated) {
				mapDataService.routeTypeVisibility.set(routeTypeVisibility);
				mapDataService.updateData();
			}

			SimplifyRoutesPipe.sortRoutes(selectedRouteVariations);
			Object.values(tempStationConnections).forEach(stationConnection => mapSelectionService.selectedStationConnections.push(stationConnection));
			mapSelectionService.select("route");
			return selectedRouteVariations;
		}, () => mapSelectionService.reset("route"), () => {
			// empty
		}, () => {
			// empty
		}, 0, dimensionService);
	}
}
