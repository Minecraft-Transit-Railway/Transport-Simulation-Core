import {Component} from "@angular/core";
import {Arrival, StationService} from "../../service/station.service";
import {MatIcon} from "@angular/material/icon";
import {NgFor, NgIf, NgStyle} from "@angular/common";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {MatButtonToggle, MatButtonToggleGroup} from "@angular/material/button-toggle";
import {MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle} from "@angular/material/expansion";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {MatDivider} from "@angular/material/divider";
import {MatChipListbox, MatChipOption} from "@angular/material/chips";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {FormatDatePipe} from "../../pipe/formatDatePipe";

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
		MatDivider,
		MatChipListbox,
		MatChipOption,
		NgStyle,
		FormatColorPipe,
		FormatDatePipe,
		NgIf,
	],
	templateUrl: "./station.component.html",
	styleUrl: "./station.component.css",
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

	getActiveRoutes() {
		return this.stationService.routes;
	}

	getArrivals(chipList: MatChipListbox) {
		try {
			const selected = (chipList.selected as MatChipOption[]).map(option => option.id);
			const newArrivals: Arrival[] = [];
			this.stationService.arrivals.forEach(arrival => {
				if (newArrivals.length < 10 && (selected.length == 0 || selected.includes(arrival.key))) {
					newArrivals.push(arrival);
				}
			});
			return newArrivals;
		} catch (e) {
			return [];
		}
	}
}
