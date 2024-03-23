import {Component} from "@angular/core";
import {StationService} from "../../service/station.service";
import {MatIcon} from "@angular/material/icon";
import {NgFor} from "@angular/common";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {MatButtonToggle, MatButtonToggleGroup} from "@angular/material/button-toggle";
import {MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle} from "@angular/material/expansion";
import {FormatTimePipe} from "../../pipe/formatDatePipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {MatDivider} from "@angular/material/divider";

@Component({
	selector: "app-station",
	standalone: true,
	imports: [
		MatIcon,
		NgFor,
		SplitNamePipe,
		MatButtonToggle,
		MatButtonToggleGroup,
		MatExpansionPanel,
		MatExpansionPanelHeader,
		MatExpansionPanelTitle,
		FormatTimePipe,
		FormatNamePipe,
		MatDivider
	],
	templateUrl: "./station.component.html",
	styleUrl: "./station.component.css"
})
export class StationComponent {

	constructor(private readonly stationService: StationService) {
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

	getArrivals() {
		return this.stationService.getArrivals();
	}
}
