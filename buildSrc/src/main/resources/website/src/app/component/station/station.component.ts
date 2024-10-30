import {Component, EventEmitter, Inject, Output} from "@angular/core";
import {Arrival, StationService} from "../../service/station.service";
import {MatIcon} from "@angular/material/icon";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {MatButtonToggle, MatButtonToggleGroup} from "@angular/material/button-toggle";
import {MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle} from "@angular/material/expansion";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {MatDivider} from "@angular/material/divider";
import {MatChipListbox, MatChipOption} from "@angular/material/chips";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {MatCheckbox} from "@angular/material/checkbox";
import {MatRipple} from "@angular/material/core";
import {MAT_DIALOG_DATA, MatDialog, MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle} from "@angular/material/dialog";
import {MatButton, MatButtonModule, MatIconButton} from "@angular/material/button";
import {MatProgressSpinner} from "@angular/material/progress-spinner";

@Component({
	selector: "app-station",
	standalone: true,
	imports: [
		MatIcon,
		SplitNamePipe,
		MatButtonToggle,
		MatButtonToggleGroup,
		MatExpansionPanel,
		MatExpansionPanelHeader,
		MatExpansionPanelTitle,
		FormatTimePipe,
		FormatNamePipe,
		MatDivider,
		MatChipListbox,
		MatChipOption,
		FormatColorPipe,
		FormatDatePipe,
		MatCheckbox,
		MatRipple,
		MatProgressSpinner,
		MatIconButton,
		MatButton,
	],
	templateUrl: "./station.component.html",
	styleUrl: "./station.component.css",
})
export class StationComponent {
	@Output() directionsOpened = new EventEmitter<void>;

	constructor(private readonly stationService: StationService, private readonly dialog: MatDialog) {
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

	getActiveRoutes() {
		return this.stationService.routes;
	}

	getArrivals(chipList: MatChipListbox, showTerminatingCheckbox: MatCheckbox): Arrival[] {
		try {
			const selected = (chipList.selected as MatChipOption[]).map(option => option.id);
			const newArrivals: Arrival[] = [];
			this.stationService.arrivals.forEach(arrival => {
				if (newArrivals.length < 10 && (arrival.isContinuous || arrival.getDepartureTime() >= 0) && (selected.length == 0 || selected.includes(arrival.key)) && (showTerminatingCheckbox.checked || !arrival.isTerminating)) {
					newArrivals.push(arrival);
				}
			});
			return newArrivals;
		} catch (e) {
			return [];
		}
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

	setDirectionsOrigin() {
		// TODO
	}

	setDirectionsDestination() {
		// TODO
	}

	showDetails(arrival: Arrival) {
		this.dialog.open(DialogOverviewExampleDialog, {data: arrival});
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
		FormatTimePipe,
		FormatNamePipe,
	],
	templateUrl: "arrival-dialog.html",
	styleUrl: "./station.component.css",
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
