import {Component, EventEmitter} from "@angular/core";
import {SearchComponent} from "../search/search.component";
import {DirectionsService} from "../../service/directions.service";
import {MatProgressSpinnerModule} from "@angular/material/progress-spinner";
import {RouteDisplayComponent} from "../route-display/route-display.component";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {MatExpansionModule} from "@angular/material/expansion";
import {MatIconModule} from "@angular/material/icon";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {MatSliderModule} from "@angular/material/slider";
import {MatFormField, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {FormControl, ReactiveFormsModule} from "@angular/forms";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {Route, RoutePlatform} from "../../entity/route";
import {MatCheckboxModule} from "@angular/material/checkbox";
import {MatButtonModule} from "@angular/material/button";
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatDividerModule} from "@angular/material/divider";
import {MapDataService} from "../../service/map-data.service";
import {MapSelectionService} from "../../service/map-selection.service";
import {pushIfNotExists} from "../../data/utilities";

@Component({
	selector: "app-directions",
	imports: [
		SearchComponent,
		MatProgressSpinnerModule,
		RouteDisplayComponent,
		FormatNamePipe,
		DataListEntryComponent,
		FormatDatePipe,
		MatExpansionModule,
		MatIconModule,
		MatSliderModule,
		MatFormField,
		MatInput,
		MatLabel,
		ReactiveFormsModule,
		MatCheckboxModule,
		MatButtonModule,
		MatTooltipModule,
		MatDividerModule,
	],
	templateUrl: "./directions.component.html",
	styleUrl: "./directions.component.css",
})
export class DirectionsComponent {
	protected readonly automaticRefresh = new FormControl(true);
	protected readonly setStartStationText = new EventEmitter<string>();
	protected readonly setEndStationText = new EventEmitter<string>();
	private startStationId?: string;
	private endStationId?: string;
	private maxWalkingDistanceString = "";
	private directionsCache: {
		startPosition: { x: number, y: number, z: number },
		startRoutePlatform?: RoutePlatform,
		endPosition: { x: number, y: number, z: number },
		endRoutePlatform?: RoutePlatform,
		intermediateRoutePlatforms: RoutePlatform[],
		route?: Route,
		icon: string,
		startTime: number,
		endTime: number,
		distance: number,
	}[] = [];

	constructor(private readonly directionsService: DirectionsService, private readonly mapDataService: MapDataService, private readonly mapSelectionService: MapSelectionService, private readonly formatNamePipe: FormatNamePipe, private readonly formatTimePipe: FormatTimePipe) {
		directionsService.directionsPanelOpened.subscribe((stationDetails) => {
			if (stationDetails) {
				this.onClickStation(stationDetails.stationId, stationDetails.isStartStation);
			} else {
				this.checkStatus();
			}
		});
		directionsService.dataProcessed.subscribe(() => {
			if (this.automaticRefresh.getRawValue()) {
				this.refreshDirections();
			}
		});
	}

	onClickStation(stationId: string | undefined, isStartStation: boolean) {
		(isStartStation ? this.setStartStationText : this.setEndStationText).emit(stationId ? this.formatNamePipe.transform(this.mapDataService.stations.find(station => station.id === stationId)?.name ?? "") : "");

		if (isStartStation) {
			this.startStationId = stationId;
		} else {
			this.endStationId = stationId;
		}

		this.checkStatus();
	}

	swapStations(startStation: SearchComponent, endStation: SearchComponent) {
		const tempId = this.startStationId;
		this.startStationId = this.endStationId;
		this.endStationId = tempId;
		const tempName = startStation.getText();
		this.setStartStationText.emit(endStation.getText());
		this.setEndStationText.emit(tempName);
		this.checkStatus();
	}

	updateMaxWalkingDistance(value: string) {
		this.maxWalkingDistanceString = value;
		this.checkStatus();
	}

	getDefaultMaxWalkingDistance() {
		return this.directionsService.defaultMaxWalkingDistance;
	}

	getDirections() {
		return this.directionsCache;
	}

	isValid() {
		return !!this.startStationId && !!this.endStationId && this.startStationId !== this.endStationId;
	}

	isLoading() {
		return this.directionsService.isLoading();
	}

	refreshDirections() {
		this.directionsCache = [...this.directionsService.getDirections()];
		this.mapSelectionService.selectedStationConnections.length = 0;
		this.mapSelectionService.selectedStations.length = 0;
		let mapUpdated = false;

		this.directionsCache.forEach(direction => {
			if (direction.startRoutePlatform && direction.endRoutePlatform) {
				const routePlatforms = [direction.startRoutePlatform, ...direction.intermediateRoutePlatforms, direction.endRoutePlatform];
				for (let i = 1; i < routePlatforms.length; i++) {
					const routePlatform1 = routePlatforms[i - 1];
					const routePlatform2 = routePlatforms[i];
					const reverse = routePlatform1.station.id > routePlatform2.station.id;
					const newStationId1 = reverse ? routePlatform2.station.id : routePlatform1.station.id;
					const newStationId2 = reverse ? routePlatform1.station.id : routePlatform2.station.id;

					if (direction.route) {
						this.mapSelectionService.selectedStationConnections.push({stationIds: [newStationId1, newStationId2], routeColor: direction.route.color});
						if (this.mapDataService.routeTypeVisibility[direction.route.type] === "HIDDEN") {
							this.mapDataService.routeTypeVisibility[direction.route.type] = "SOLID";
							mapUpdated = true;
						}
					}

					pushIfNotExists(this.mapSelectionService.selectedStations, newStationId1);
					pushIfNotExists(this.mapSelectionService.selectedStations, newStationId2);
				}
			}
		});

		if (mapUpdated) {
			this.mapDataService.updateData();
		}

		this.mapSelectionService.select("directions");
	}

	getStationName(position: { x: number, y: number, z: number }, routePlatform?: RoutePlatform) {
		return routePlatform ? this.formatNamePipe.transform(routePlatform.station.name) : `(${position.x}, ${position.y}, ${position.z})`;
	}

	getPlatformName(routePlatform?: RoutePlatform) {
		return routePlatform ? `Platform ${this.formatNamePipe.transform(routePlatform.name)}` : "";
	}

	getRouteName(route: Route) {
		return `${this.formatNamePipe.transform(route.name.split("||")[0])} ${this.formatNamePipe.transform(route.number)}`;
	}

	getRouteDestination(route: Route) {
		return route.circularState === "NONE" ? this.formatNamePipe.transform(route.routePlatforms[route.routePlatforms.length - 1].station.name) : "";
	}

	getRouteColor(index: number) {
		return this.getDirections()[index]?.route?.color ?? -1;
	}

	getDuration(direction: { startTime: number, endTime: number }) {
		return this.formatTimePipe.transform(Math.round((direction.endTime - direction.startTime) / 1000), "");
	}

	getDistanceLabel(direction: { distance: number }) {
		const roundedDistance = Math.round(direction.distance / 100) / 10;
		return roundedDistance > 0 ? `${roundedDistance} km` : "";
	}

	getCircularIcon(route: Route) {
		return SimplifyRoutesPipe.getCircularStateIcon(route.circularState);
	}

	sameStation(direction: { startRoutePlatform?: RoutePlatform, endRoutePlatform?: RoutePlatform }) {
		return direction.startRoutePlatform && direction.endRoutePlatform && (direction.startRoutePlatform.station.id === direction.endRoutePlatform.station.id || direction.startRoutePlatform.station.connections.some(station => station.id === direction.endRoutePlatform?.station?.id));
	}

	private checkStatus() {
		if (this.isValid()) {
			const startStation = this.mapDataService.stations.find(station => station.id === this.startStationId);
			const endStation = this.mapDataService.stations.find(station => station.id === this.endStationId);
			if (startStation && endStation) {
				this.directionsService.selectStations(startStation, endStation, this.maxWalkingDistanceString);
			} else {
				this.directionsService.clear();
			}
		} else {
			this.directionsService.clear();
		}
	}
}
