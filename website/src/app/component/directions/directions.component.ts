import {Component} from "@angular/core";
import {DirectionsSegment, DirectionsService} from "../../service/directions.service";
import {NgForOf, NgIf} from "@angular/common";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {MatIcon} from "@angular/material/icon";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {FormatDatePipe} from "../../pipe/formatDatePipe";

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
	],
	templateUrl: "./directions.component.html",
	styleUrl: "./directions.component.css",
})
export class DirectionsComponent {

	constructor(protected directionsService: DirectionsService) {
	}

	isLoading() {
		return this.directionsService.isLoading();
	}

	getDirections(): DirectionsSegment[] {
		return this.directionsService.directions;
	}
}
