import {Component} from "@angular/core";
import {MapComponent} from "./component/map/map.component";
import {PanelComponent} from "./component/panel/panel.component";
import {MatButtonModule} from "@angular/material/button";
import {SearchComponent} from "./component/search/search.component";
import {StationComponent} from "./component/station/station.component";
import {StationService} from "./service/station.service";
import {FormatNamePipe} from "./pipe/formatNamePipe";
import {SideComponent} from "./component/side/side.component";
import {DataService} from "./service/data.service";
import {DirectionsComponent} from "./component/directions/directions.component";
import {DirectionsService} from "./service/directions.service";
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatIconModule} from "@angular/material/icon";

@Component({
	selector: "app-root",
	standalone: true,
	imports: [
		MapComponent,
		PanelComponent,
		MatButtonModule,
		MatIconModule,
		SearchComponent,
		StationComponent,
		SideComponent,
		DirectionsComponent,
		MatTooltipModule,
	],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent {

	constructor(private readonly dataService: DataService, private readonly stationService: StationService, private readonly directionsService: DirectionsService, private readonly formatNamePipe: FormatNamePipe) {
	}

	getTitle() {
		return document.title;
	}

	getSelectedStationName() {
		const station = this.stationService.getSelectedStation();
		return station == undefined ? "" : this.formatNamePipe.transform(station.name);
	}

	onClickStation(stationId: string, sideStation: SideComponent, sideDirections: SideComponent, zoomToStation: boolean) {
		this.stationService.setStation(stationId);
		sideStation.open();
		sideDirections.close();
		const station = this.dataService.getAllStations().filter(station => station.id === stationId)[0];
		if (station) {
			if (station.types.every(routeType => this.dataService.getRouteTypes()[routeType] === 0)) {
				station.types.forEach(routeType => this.dataService.getRouteTypes()[routeType] = 1);
				this.dataService.updateData();
			}
			if (zoomToStation) {
				this.dataService.animateCenter(station.x, station.z);
			}
		}
	}

	onCloseStation() {
		this.stationService.clear();
	}

	onCloseDirections() {
		console.log(this);
		this.directionsService.clear();
	}
}
