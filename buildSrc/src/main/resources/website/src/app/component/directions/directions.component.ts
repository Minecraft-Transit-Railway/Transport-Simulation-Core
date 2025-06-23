import {Component} from "@angular/core";
import {SearchComponent} from "../search/search.component";
import {DirectionsService} from "../../service/directions.service";
import {RouteDisplayComponent} from "../route-display/route-display.component";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormControl, FormGroup, ReactiveFormsModule} from "@angular/forms";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {Route, RoutePlatform} from "../../entity/route";
import {MapDataService} from "../../service/map-data.service";
import {MapSelectionService} from "../../service/map-selection.service";
import {pushIfNotExists} from "../../data/utilities";
import {ProgressSpinnerModule} from "primeng/progressspinner";
import {AccordionModule} from "primeng/accordion";
import {SliderModule} from "primeng/slider";
import {InputTextModule} from "primeng/inputtext";
import {CheckboxModule} from "primeng/checkbox";
import {ButtonModule} from "primeng/button";
import {TooltipModule} from "primeng/tooltip";
import {DividerModule} from "primeng/divider";
import {FloatLabelModule} from "primeng/floatlabel";
import {InputNumberModule} from "primeng/inputnumber";
import {SearchData} from "../../entity/searchData";

@Component({
	selector: "app-directions",
	imports: [
		FloatLabelModule,
		InputNumberModule,
		ProgressSpinnerModule,
		AccordionModule,
		SliderModule,
		InputTextModule,
		CheckboxModule,
		ButtonModule,
		TooltipModule,
		DividerModule,
		SearchComponent,
		RouteDisplayComponent,
		FormatNamePipe,
		DataListEntryComponent,
		FormatDatePipe,
		ReactiveFormsModule,
	],
	templateUrl: "./directions.component.html",
	styleUrl: "./directions.component.css",
})
export class DirectionsComponent {
	protected readonly formGroup = new FormGroup({
		startStation: new FormControl<{ value: SearchData } | undefined>(undefined),
		endStation: new FormControl<{ value: SearchData } | undefined>(undefined),
		automaticRefresh: new FormControl(true),
		maxWalkingDistance: new FormControl(this.directionsService.defaultMaxWalkingDistance),
	});
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
	private forceRefresh = false;

	constructor(private readonly directionsService: DirectionsService, private readonly mapDataService: MapDataService, private readonly mapSelectionService: MapSelectionService, private readonly formatNamePipe: FormatNamePipe, private readonly formatTimePipe: FormatTimePipe) {
		directionsService.directionsPanelOpened.subscribe((stationDetails) => {
			if (stationDetails) {
				this.onClickStation(stationDetails.stationId, stationDetails.isStartStation);
			} else {
				this.checkStatus();
			}
		});
		directionsService.dataProcessed.subscribe(() => {
			if (this.forceRefresh || this.formGroup.getRawValue().automaticRefresh) {
				this.forceRefresh = false;
				this.refreshDirections();
			}
		});
	}

	onClickStation(stationId: string | undefined, isStartStation: boolean) {
		const station = stationId ? this.mapDataService.stations.find(station => station.id === stationId) : undefined;

		if (isStartStation) {
			this.formGroup.patchValue({startStation: station ? {value: {key: station.id, icons: station.getIcons(), color: station.color, name: station.name, number: "", isStation: true}} : undefined});
		} else {
			this.formGroup.patchValue({endStation: station ? {value: {key: station.id, icons: station.getIcons(), color: station.color, name: station.name, number: "", isStation: true}} : undefined});
		}

		this.checkStatus();
	}

	swapStations() {
		const data = this.formGroup.getRawValue();
		this.formGroup.patchValue({startStation: data.endStation, endStation: data.startStation});
		this.checkStatus();
	}

	cannotSwap() {
		const data = this.formGroup.getRawValue();
		return !data.startStation && !data.endStation;
	}

	updateMaxWalkingDistance() {
		this.checkStatus();
	}

	getDirections() {
		return this.directionsCache;
	}

	isValid() {
		const data = this.formGroup.getRawValue();
		return data.startStation && data.endStation && data.startStation !== data.endStation;
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

	cannotManuallyRefresh() {
		return this.isLoading() || this.formGroup.getRawValue().automaticRefresh || !this.isValid();
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
			const data = this.formGroup.getRawValue();
			const startStation = this.mapDataService.stations.find(station => station.id === data.startStation?.value?.key);
			const endStation = this.mapDataService.stations.find(station => station.id === data.endStation?.value?.key);
			if (startStation && endStation) {
				this.directionsService.selectStations(startStation, endStation, (data.maxWalkingDistance ?? this.directionsService.defaultMaxWalkingDistance).toString());
				this.forceRefresh = true;
			} else {
				this.directionsService.clear();
			}
		} else {
			this.directionsService.clear();
		}
	}
}
