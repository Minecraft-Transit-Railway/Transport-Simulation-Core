import {Component, EventEmitter, Inject, Output} from "@angular/core";
import {Arrival, StationService} from "../../service/station.service";
import {MatIcon, MatIconModule} from "@angular/material/icon";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {MatDividerModule} from "@angular/material/divider";
import {MatChipListbox, MatChipOption, MatChipsModule} from "@angular/material/chips";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {MatCheckboxModule} from "@angular/material/checkbox";
import {MAT_DIALOG_DATA, MatDialog, MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle} from "@angular/material/dialog";
import {MatButtonModule} from "@angular/material/button";
import {MatProgressSpinnerModule} from "@angular/material/progress-spinner";
import {MatTooltipModule} from "@angular/material/tooltip";
import {DataService, StationWithPosition} from "../../service/data.service";
import {MatTabsModule} from "@angular/material/tabs";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {ROUTE_TYPES} from "../../data/routeType";

@Component({
	selector: "app-station-panel",
	standalone: true,
	imports: [
		MatIconModule,
		SplitNamePipe,
		FormatTimePipe,
		FormatNamePipe,
		MatDividerModule,
		MatChipsModule,
		FormatColorPipe,
		FormatDatePipe,
		MatCheckboxModule,
		MatProgressSpinnerModule,
		MatButtonModule,
		MatTooltipModule,
		MatTabsModule,
		DataListEntryComponent,
	],
	templateUrl: "./station-panel.component.html",
	styleUrl: "./station-panel.component.css",
})
export class StationPanelComponent {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>;

	constructor(private readonly dataService: DataService, private readonly stationService: StationService, private readonly dialog: MatDialog) {
	}

	getStation() {
		return this.stationService.getSelectedStation();
	}

	getCoordinatesText() {
		const station = this.stationService.getSelectedStation();
		return station == undefined ? "" : `${Math.round(station.x)}, ${Math.round(station.y)}, ${Math.round(station.z)}`;
	}

	getZoneText() {
		const station = this.stationService.getSelectedStation();
		return station == undefined ? "" : `${station.zone1}, ${station.zone2}, ${station.zone3}`;
	}

	getConnections(): StationWithPosition[] {
		const station = this.stationService.getSelectedStation();
		if (station == undefined) {
			return [];
		} else {
			const stations: StationWithPosition[] = [];
			this.dataService.getAllStations().forEach(otherStation => {
				if (station.connections.includes(otherStation.id)) {
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
		return circularState === "CLOCKWISE" ? "rotate_right" : circularState === "ANTICLOCKWISE" ? "rotate_left" : "";
	}

	mapRouteVariations(variations: string[]): [string, string][] {
		return variations.map(variation => [variation, ""]);
	}

	updateArrivalFilter(chipList: MatChipListbox, filterArrivalShowTerminating: boolean) {
		try {
			this.stationService.updateArrivalFilter((chipList.selected as MatChipOption[]).map(option => option.id), filterArrivalShowTerminating);
		} catch (e) {
			this.stationService.updateArrivalFilter([], filterArrivalShowTerminating);
		}
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

	copyLocation(copyIconButton: MatIcon) {
		copyIconButton.fontIcon = "check";
		const station = this.stationService.getSelectedStation();
		navigator.clipboard.writeText(station == undefined ? "" : `${Math.round(station.x)} ${Math.round(station.y)} ${Math.round(station.z)}`).then();
		setTimeout(() => copyIconButton.fontIcon = "content_copy", 1000);
	}

	focus() {
		const station = this.stationService.getSelectedStation();
		if (station) {
			this.dataService.animateCenter(station.x, station.z);
		}
	}

	showDetails(arrival: Arrival) {
		this.dialog.open(DialogOverviewExampleDialog, {data: arrival});
	}

	mapConnectionTypes(types: string[]) {
		return types.map(type => ROUTE_TYPES[type].icon);
	}
}

@Component({
	selector: "dialog-arrival-dialog",
	standalone: true,
	imports: [
		MatButtonModule,
		MatDialogTitle,
		MatDialogContent,
		MatDialogActions,

	],
	templateUrl: "arrival-dialog.html",
	styleUrl: "./station-panel.component.css",
})
export class DialogOverviewExampleDialog {
	constructor(
		public dialogRef: MatDialogRef<DialogOverviewExampleDialog>,
		@Inject(MAT_DIALOG_DATA) public data: Arrival,
	) {
	}

	onClose(): void {
		this.dialogRef.close();
	}
}
