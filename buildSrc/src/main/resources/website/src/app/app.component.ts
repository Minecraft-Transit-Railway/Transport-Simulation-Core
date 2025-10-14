import {Component, inject} from "@angular/core";
import {MapComponent} from "./component/map/map.component";
import {StationPanelComponent} from "./component/station-panel/station-panel.component";
import {StationService} from "./service/station.service";
import {DrawerComponent} from "./component/drawer/drawer.component";
import {DirectionsComponent} from "./component/directions/directions.component";
import {MainPanelComponent} from "./component/main-panel/main-panel.component";
import {RouteKeyService} from "./service/route.service";
import {RoutePanelComponent} from "./component/route-panel/route-panel.component";
import {DirectionsService} from "./service/directions.service";
import {ButtonModule} from "primeng/button";
import {TooltipModule} from "primeng/tooltip";
import {ClientService} from "./service/client.service";
import {ClientPanelComponent} from "./component/client-panel/client-panel.component";

@Component({
	selector: "app-root",
	imports: [
		MapComponent,
		ButtonModule,
		TooltipModule,
		StationPanelComponent,
		DrawerComponent,
		ClientPanelComponent,
		DirectionsComponent,
		MainPanelComponent,
		RoutePanelComponent,
	],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent {
	private readonly stationService = inject(StationService);
	private readonly routeKeyService = inject(RouteKeyService);
	private readonly clientService = inject(ClientService);
	private readonly directionsService = inject(DirectionsService);


	getTitle() {
		return document.title;
	}

	onClickMain(sideMain: DrawerComponent, sideStation: DrawerComponent, sideClient: DrawerComponent, sideDirections: DrawerComponent, sideRoute: DrawerComponent) {
		sideMain.open();
		sideStation.close();
		sideClient.close();
		sideDirections.close();
		sideRoute.close();
		this.onCloseStation();
		this.onCloseClient();
		this.onCloseDirections();
		this.onCloseRoute();
	}

	onClickStation(stationId: string, sideMain: DrawerComponent, sideStation: DrawerComponent, sideClient: DrawerComponent, sideDirections: DrawerComponent, sideRoute: DrawerComponent, zoomToStation: boolean) {
		this.stationService.setStation(stationId, zoomToStation);
		sideMain.close();
		sideStation.open();
		sideClient.close();
		sideDirections.close();
		sideRoute.close();
		this.onCloseClient();
		this.onCloseDirections();
		this.onCloseRoute();
	}

	onClickRoute(routeKey: string, sideMain: DrawerComponent, sideStation: DrawerComponent, sideClient: DrawerComponent, sideDirections: DrawerComponent, sideRoute: DrawerComponent) {
		this.routeKeyService.select(routeKey);
		sideMain.close();
		sideStation.close();
		sideClient.close();
		sideDirections.close();
		sideRoute.open();
		this.onCloseStation();
		this.onCloseClient();
		this.onCloseDirections();
	}

	onClickClient(clientId: string, sideMain: DrawerComponent, sideStation: DrawerComponent, sideClient: DrawerComponent, sideDirections: DrawerComponent, sideRoute: DrawerComponent) {
		this.clientService.setClient(clientId);
		sideMain.close();
		sideStation.close();
		sideClient.open();
		sideDirections.close();
		sideRoute.close();
		this.onCloseStation();
		this.onCloseRoute();
		this.onCloseDirections();
	}

	onOpenDirections(directionsSelection: { stationDetails?: { stationId: string, isStartStation: boolean }, clientDetails?: { clientId: string, isStartClient: boolean } } | undefined, sideMain: DrawerComponent, sideStation: DrawerComponent, sideClient: DrawerComponent, sideDirections: DrawerComponent, sideRoute: DrawerComponent) {
		this.directionsService.directionsPanelOpened.emit(directionsSelection);
		sideMain.close();
		sideStation.close();
		sideClient.close();
		sideDirections.open();
		sideRoute.close();
		this.onCloseStation();
		this.onCloseClient();
		this.onCloseRoute();
	}

	onCloseStation() {
		this.stationService.clear();
	}

	onCloseClient() {
		this.clientService.clear();
	}

	onCloseDirections() {
		this.directionsService.clear();
	}

	onCloseRoute() {
		this.routeKeyService.clear();
	}
}
