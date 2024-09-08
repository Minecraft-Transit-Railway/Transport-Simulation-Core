import {Component} from "@angular/core";
import {DirectionsSegment, DirectionsService} from "../../service/directions.service";
import {NgForOf, NgIf} from "@angular/common";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {MatIcon} from "@angular/material/icon";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {SearchComponent} from "../search/search.component";
import {DataService} from "../../service/data.service";

@Component({
	selector: "app-directions",
	standalone: true,
	imports: [
		NgForOf,
		MatProgressSpinner,
		NgIf,
		FormatTimePipe,
		FormatNamePipe,
		MatIcon,
		FormatColorPipe,
		FormatDatePipe,
		SearchComponent
	],
	templateUrl: "./directions.component.html",
	styleUrl: "./directions.component.css",
})
export class DirectionsComponent {

	constructor(protected directionsService: DirectionsService, private readonly dataService: DataService) {
	}

	isLoading() {
		return this.directionsService.isLoading();
	}

	getDirections(): DirectionsSegment[] {
		return this.directionsService.directions;
	}

	setOrigin(id: string) {
		this.directionsService.setOriginStation(this.dataService.getAllStations().filter(station => station.id === id)[0]);
	}

	setDestination(id: string) {
		this.directionsService.setDestinationStation(this.dataService.getAllStations().filter(station => station.id === id)[0]);
	}
}
