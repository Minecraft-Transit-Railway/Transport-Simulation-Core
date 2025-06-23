import {Component, EventEmitter, Output} from "@angular/core";
import {Arrival, StationService} from "../../service/station.service";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {MapDataService} from "../../service/map-data.service";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {TitleComponent} from "../title/title.component";
import {Station} from "../../entity/station";
import {TooltipModule} from "primeng/tooltip";
import {Button, ButtonModule} from "primeng/button";
import {TabsModule} from "primeng/tabs";
import {CheckboxModule} from "primeng/checkbox";
import {DividerModule} from "primeng/divider";
import {DialogModule} from "primeng/dialog";
import {ProgressSpinnerModule} from "primeng/progressspinner";
import {ChipModule} from "primeng/chip";
import {PrimeIcons} from "primeng/api";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {SplitNamePipe} from "../../pipe/splitNamePipe";

@Component({
	selector: "app-station-panel",
	imports: [
		ButtonModule,
		TooltipModule,
		TabsModule,
		CheckboxModule,
		ChipModule,
		DividerModule,
		ProgressSpinnerModule,
		DialogModule,
		FormatNamePipe,
		FormatColorPipe,
		FormatDatePipe,
		FormatTimePipe,
		SplitNamePipe,
		DataListEntryComponent,
		TitleComponent,
	],
	templateUrl: "./station-panel.component.html",
	styleUrl: "./station-panel.component.css",
})
export class StationPanelComponent {
	protected dialogData?: Arrival;
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<{ stationId: string, isStartStation: boolean }>;

	constructor(private readonly dataService: MapDataService, private readonly stationService: StationService) {
	}

	getStation() {
		return this.stationService.getSelectedData();
	}

	getStationColor() {
		const station = this.stationService.getSelectedData();
		return station === undefined ? undefined : station.color;
	}

	getCoordinatesText() {
		const station = this.stationService.getSelectedData();
		return station === undefined ? "" : `${Math.round(station.x)}, ${Math.round(station.y)}, ${Math.round(station.z)}`;
	}

	getZoneText() {
		const station = this.stationService.getSelectedData();
		return station === undefined ? "" : `${station.zone1}, ${station.zone2}, ${station.zone3}`;
	}

	getConnections(): Station[] {
		const station = this.stationService.getSelectedData();
		if (station === undefined) {
			return [];
		} else {
			const stations: Station[] = [];
			this.dataService.stations.forEach(otherStation => {
				if (station.connections.some(connectingStation => connectingStation.id === otherStation.id)) {
					stations.push(otherStation);
				}
			});
			return stations;
		}
	}

	getActiveRoutes() {
		return this.stationService.arrivalsRoutes;
	}

	getArrivals() {
		return this.stationService.getArrivals();
	}

	getRoutes() {
		return this.stationService.routesAtStation;
	}

	getCircularStateIcon(circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE") {
		return SimplifyRoutesPipe.getCircularStateIcon(circularState);
	}

	mapRouteVariations(variations: string[]): [string, string][] {
		return variations.map(variation => [variation, ""]);
	}

	updateArrivalFilter(filterArrivalShowTerminating: boolean, toggleRouteKey?: string) {
		this.stationService.updateArrivalFilter(filterArrivalShowTerminating, toggleRouteKey);
	}

	routeFiltered(routeKey: string) {
		return this.stationService.routeFiltered(routeKey);
	}

	resetArrivalFilter() {
		this.stationService.resetArrivalFilter();
	}

	getHasTerminating() {
		return this.stationService.getHasTerminating();
	}

	isLoading() {
		return this.stationService.isLoading();
	}

	copyLocation(copyIconButton: Button) {
		copyIconButton.icon = PrimeIcons.CHECK;
		const station = this.stationService.getSelectedData();
		navigator.clipboard.writeText(station === undefined ? "" : `${Math.round(station.x)} ${Math.round(station.y)} ${Math.round(station.z)}`).then();
		setTimeout(() => copyIconButton.icon = PrimeIcons.COPY, 1000);
	}

	focus() {
		const station = this.stationService.getSelectedData();
		if (station) {
			this.dataService.animateMap.emit({x: station.x, z: station.z});
		}
	}

	openDirections(isStartStation: boolean) {
		const station = this.stationService.getSelectedData();
		if (station) {
			this.directionsOpened.emit({stationId: station.id, isStartStation});
		}
	}

	showDetails(arrival: Arrival) {
		this.dialogData = arrival;
	}

	getRouteKey(route: { color: number, name: string, number: string }) {
		return SimplifyRoutesPipe.getRouteKey(route);
	}
}
