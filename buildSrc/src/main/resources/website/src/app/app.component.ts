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
import {RouteService} from "./service/route.service";
import {RoutePanelComponent} from "./component/route-panel/route-panel.component";

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
		RoutePanelComponent,
	],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent {

	constructor(private readonly stationService: StationService, private readonly routeService: RouteService, private readonly directionsService: DirectionsService) {
	}

	getTitle() {
		return document.title;
	}

	onClickMain(sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent) {
		sideMain.open();
		sideStation.close();
		sideDirections.close();
		sideRoute.close();
	}

	onClickStation(stationId: string, sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent, zoomToStation: boolean) {
		this.stationService.setStation(stationId, zoomToStation);
		sideMain.close();
		sideStation.open();
		sideDirections.close();
		sideRoute.close();
	}

	onClickRoute(routeKey: string, sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent) {
		this.routeService.setRoute(routeKey);
		sideMain.close();
		sideStation.close();
		sideDirections.close();
		sideRoute.open();
	}

	onCloseStation() {
		this.stationService.clear();
	}

	onCloseDirections() {
		console.log(this);
		this.directionsService.clear();
	}

	onCloseRoute() {
		this.routeService.clear();
	}
}
