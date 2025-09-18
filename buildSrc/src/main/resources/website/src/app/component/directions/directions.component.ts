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
import {Route} from "../../entity/route";
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
import {Station} from "../../entity/station";
import {ClientsService} from "../../service/clients.service";

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
		startInput: new FormControl<{ value: SearchData } | undefined>(undefined),
		endInput: new FormControl<{ value: SearchData } | undefined>(undefined),
		maxWalkingDistance: new FormControl(this.directionsService.defaultMaxWalkingDistance),
	});
	private directionsCache: {
		startStation?: Station,
		endStation?: Station,
		startPlatformName?: string,
		endPlatformName?: string,
		intermediateStations: Station[],
		route?: Route,
		icon: string,
		startTime: number,
		endTime: number,
		distance: number,
	}[] = [];
	private forceRefresh = false;

	constructor(private readonly directionsService: DirectionsService, private readonly mapDataService: MapDataService, private readonly clientsService: ClientsService, private readonly mapSelectionService: MapSelectionService, private readonly formatNamePipe: FormatNamePipe, private readonly formatTimePipe: FormatTimePipe) {
		directionsService.directionsPanelOpened.subscribe((directionsSelection) => {
			if (directionsSelection) {
				this.onClickData(directionsSelection);
			} else {
				this.checkStatus();
			}
		});
		directionsService.dataProcessed.subscribe(() => {
			if (this.forceRefresh || this.canAutomaticallyRefresh()) {
				this.forceRefresh = false;
				this.refreshDirections();
			}
		});
	}

	onClearInput(isStartInput: boolean) {
		this.formGroup.patchValue(isStartInput ? {startInput: undefined} : {endInput: undefined});
		this.checkStatus();
	}

	onClickStation(stationId: string | undefined, isStartStation: boolean) {
		if (stationId) {
			this.onClickData({stationDetails: {stationId, isStartStation}});
		}
	}

	onClickClient(clientId: string | undefined, isStartClient: boolean) {
		if (clientId) {
			this.onClickData({clientDetails: {clientId, isStartClient}});
		}
	}

	swapStations() {
		const data = this.formGroup.getRawValue();
		this.formGroup.patchValue({startInput: data.endInput, endInput: data.startInput});
		this.checkStatus();
	}

	cannotSwap() {
		const data = this.formGroup.getRawValue();
		return !data.startInput && !data.endInput;
	}

	updateMaxWalkingDistance() {
		this.checkStatus();
	}

	getDirections() {
		return this.directionsCache;
	}

	isValid() {
		const data = this.formGroup.getRawValue();
		return data.startInput && data.endInput && data.startInput !== data.endInput;
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
			if (direction.startStation && direction.endStation) {
				const stations = [direction.startStation, ...direction.intermediateStations, direction.endStation];
				for (let i = 1; i < stations.length; i++) {
					const station1 = stations[i - 1];
					const station2 = stations[i];
					const reverse = station1.id > station2.id;
					const newStationId1 = reverse ? station2.id : station1.id;
					const newStationId2 = reverse ? station1.id : station2.id;

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
		return this.isLoading() || this.canAutomaticallyRefresh() || !this.isValid();
	}

	getStationName(station?: Station) {
		return station ? this.formatNamePipe.transform(station.name) : "(Untitled)";
	}

	getPlatformName(platformName?: string) {
		return platformName ? `Platform ${this.formatNamePipe.transform(platformName)}` : "";
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

	sameStation(direction: { startStation?: Station, endStation?: Station }) {
		return direction.startStation && direction.endStation && (direction.startStation.id === direction.endStation.id || direction.startStation.connections.some(station => station.id === direction.endStation?.id));
	}

	onClickData(directionsSelection: { stationDetails?: { stationId: string, isStartStation: boolean }, clientDetails?: { clientId: string, isStartClient: boolean } }) {
		const {stationDetails, clientDetails} = directionsSelection;

		if (stationDetails) {
			const station = stationDetails.stationId ? this.mapDataService.stations.find(station => station.id === stationDetails.stationId) : undefined;
			if (stationDetails.isStartStation) {
				this.formGroup.patchValue({startInput: station ? {value: {key: station.id, icons: station.getIcons(), color: station.color, name: station.name, number: "", type: "station"}} : undefined});
			} else {
				this.formGroup.patchValue({endInput: station ? {value: {key: station.id, icons: station.getIcons(), color: station.color, name: station.name, number: "", type: "station"}} : undefined});
			}
		} else if (clientDetails) {
			const client = this.clientsService.getClient(clientDetails.clientId);
			if (clientDetails.isStartClient) {
				this.formGroup.patchValue({startInput: client ? {value: {key: clientDetails.clientId, icons: [], name: client.name, number: "", type: "client"}} : undefined});
			} else {
				this.formGroup.patchValue({endInput: client ? {value: {key: clientDetails.clientId, icons: [], name: client.name, number: "", type: "client"}} : undefined});
			}
		}

		this.checkStatus();
	}

	private checkStatus() {
		if (this.isValid()) {
			const data = this.formGroup.getRawValue();
			const startStation = data.startInput?.value?.type === "station" ? this.mapDataService.stations.find(station => station.id === data.startInput?.value?.key) : undefined;
			const endStation = data.endInput?.value?.type === "station" ? this.mapDataService.stations.find(station => station.id === data.endInput?.value?.key) : undefined;
			const startClientId = data.startInput?.value?.type === "client" ? data.startInput?.value?.key : undefined;
			const endClientId = data.endInput?.value?.type === "client" ? data.endInput?.value?.key : undefined;

			if ((startStation || startClientId) && (endStation || endClientId)) {
				this.directionsService.selectData(startStation, endStation, startClientId, endClientId, (data.maxWalkingDistance ?? this.directionsService.defaultMaxWalkingDistance).toString());
				this.forceRefresh = true;
			} else {
				this.directionsService.clear();
			}
		} else {
			this.directionsService.clear();
		}
	}

	private canAutomaticallyRefresh() {
		const data = this.formGroup.getRawValue();
		return data.startInput && data.startInput.value.type === "client" || data.endInput && data.endInput.value.type === "client";
	}
}
