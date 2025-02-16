import {Component} from "@angular/core";
import {MapComponent} from "./component/map/map.component";
import {MatButtonModule} from "@angular/material/button";
import {SearchComponent} from "./component/search/search.component";
import {StationPanelComponent} from "./component/station-panel/station-panel.component";
import {StationService} from "./service/station.service";
import {SidenavComponent} from "./component/sidenav/sidenav.component";
import {DirectionsComponent} from "./component/directions/directions.component";
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatIconModule} from "@angular/material/icon";
import {MainPanelComponent} from "./component/panel/main-panel.component";
import {RouteKeyService} from "./service/route.service";
import {RoutePanelComponent} from "./component/route-panel/route-panel.component";
import {ThemeService} from "./service/theme.service";
import {DirectionsService} from "./service/directions.service";

@Component({
	selector: "app-root",
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

	constructor(private readonly themeService: ThemeService, private readonly stationService: StationService, private readonly routeKeyService: RouteKeyService, private readonly directionsService: DirectionsService) {
	}

	getTitle() {
		return document.title;
	}

	onClickMain(sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent) {
		sideMain.open();
		sideStation.close();
		sideDirections.close();
		sideRoute.close();
		this.onCloseStation();
		this.onCloseDirections();
		this.onCloseRoute();
	}

	onClickStation(stationId: string, sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent, zoomToStation: boolean) {
		this.stationService.setStation(stationId, zoomToStation);
		sideMain.close();
		sideStation.open();
		sideDirections.close();
		sideRoute.close();
		this.onCloseDirections();
		this.onCloseRoute();
	}

	onClickRoute(routeKey: string, sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent) {
		this.routeKeyService.select(routeKey);
		sideMain.close();
		sideStation.close();
		sideDirections.close();
		sideRoute.open();
		this.onCloseStation();
		this.onCloseDirections();
	}

	onOpenDirections(stationDetails: { stationId: string, isStartStation: boolean } | undefined, sideMain: SidenavComponent, sideStation: SidenavComponent, sideDirections: SidenavComponent, sideRoute: SidenavComponent) {
		this.directionsService.directionsPanelOpened.emit(stationDetails);
		sideMain.close();
		sideStation.close();
		sideDirections.open();
		sideRoute.close();
		this.onCloseStation();
		this.onCloseRoute();
	}

	onCloseStation() {
		this.stationService.clear();
	}

	onCloseDirections() {
		this.directionsService.clear();
	}

	onCloseRoute() {
		this.routeKeyService.clear();
	}

	isDarkTheme() {
		return this.themeService.isDarkTheme();
	}
}
