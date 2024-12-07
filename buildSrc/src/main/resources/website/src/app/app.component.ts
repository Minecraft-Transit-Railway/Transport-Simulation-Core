import {Component} from "@angular/core";
import {MapComponent} from "./component/map/map.component";
import {MatButtonModule} from "@angular/material/button";
import {SearchComponent} from "./component/search/search.component";
import {StationPanelComponent} from "./component/station-panel/station-panel.component";
import {StationService} from "./service/station.service";
import {SidenavComponent} from "./component/sidenav/sidenav.component";
import {DirectionsComponent} from "./component/directions/directions.component";
import {DirectionsService} from "./service/directions.service";
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatIconModule} from "@angular/material/icon";
import {MainPanelComponent} from "./component/panel/main-panel.component";

@Component({
	selector: "app-root",
	standalone: true,
	imports: [
		MapComponent,
		MatButtonModule,
		MatIconModule,
		SearchComponent,
		StationPanelComponent,
		SidenavComponent,
		DirectionsComponent,
		MatTooltipModule,
		MainPanelComponent,
	],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent {

	constructor(private readonly stationService: StationService, private readonly directionsService: DirectionsService) {
	}

	getTitle() {
		return document.title;
	}

	onClickStation(stationId: string, sideStation: SidenavComponent, sideDirections: SidenavComponent, zoomToStation: boolean) {
		this.stationService.setStation(stationId, zoomToStation);
		sideStation.open();
		sideDirections.close();
	}

	onClickRoute(routeColor: string) {
		console.log(routeColor);
	}

	onCloseStation() {
		this.stationService.clear();
	}

	onCloseDirections() {
		console.log(this);
		this.directionsService.clear();
	}
}
