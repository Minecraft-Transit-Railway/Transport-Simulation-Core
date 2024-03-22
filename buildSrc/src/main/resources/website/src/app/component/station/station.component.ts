import {Component} from "@angular/core";
import {StationService} from "../../service/station.service";
import {MatIcon} from "@angular/material/icon";

@Component({
	selector: "app-station",
	standalone: true,
	imports: [
		MatIcon
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
		return station == undefined ? "" : `${Math.round(station.x)}, ${Math.round(station.z)}`;
	}
}
