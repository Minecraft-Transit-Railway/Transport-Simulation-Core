import {Component, EventEmitter, inject, Output} from "@angular/core";
import {TooltipModule} from "primeng/tooltip";
import {CheckboxModule} from "primeng/checkbox";
import {DividerModule} from "primeng/divider";
import {SelectModule} from "primeng/select";
import {FloatLabelModule} from "primeng/floatlabel";
import {FormsModule} from "@angular/forms";
import {NgOptimizedImage} from "@angular/common";
import {TitleComponent} from "../title/title.component";
import {ClientService} from "../../service/client.service";
import {Button, ButtonDirective} from "primeng/button";
import {MapDataService} from "../../service/map-data.service";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {ROUTE_TYPES} from "../../data/routeType";

@Component({
	selector: "app-client-panel",
	imports: [
		FloatLabelModule,
		SelectModule,
		CheckboxModule,
		DividerModule,
		TooltipModule,
		FormsModule,
		NgOptimizedImage,
		TitleComponent,
		Button,
		ButtonDirective,
		DataListEntryComponent,
		FormatColorPipe,
		FormatNamePipe,
	],
	templateUrl: "./client-panel.component.html",
	styleUrl: "./client-panel.component.css",
})
export class ClientPanelComponent {
	private readonly clientService = inject(ClientService);
	private readonly mapDataService = inject(MapDataService);

	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<{ clientDetails: { clientId: string, isStartClient: boolean } }>;

	getName() {
		return this.clientService.getClient()?.name ?? "";
	}

	getImageSrc() {
		const clientId = this.clientService.getSelectedData();
		return clientId ? `https://mc-heads.net/avatar/${clientId}` : undefined;
	}

	getStation() {
		const client = this.clientService.getClient();
		return client ? client.station : undefined;
	}

	getRouteDetails() {
		const client = this.clientService.getClient();
		const route = client ? client.route : undefined;
		const routeStation1 = client ? client.routeStation1 : undefined;
		const routeStation2 = client ? client.routeStation2 : undefined;
		return route && routeStation1 && routeStation2 ? {route, routeStation1, routeStation2, icon: ROUTE_TYPES[route.type].icon} : undefined;
	}

	getCoordinatesText() {
		const client = this.clientService.getClient();
		return client ? `${Math.round(client.rawX)}, ${Math.round(client.rawZ)}` : "";
	}

	focus() {
		const clientId = this.clientService.getSelectedData();
		if (clientId) {
			this.mapDataService.animateClient.emit(clientId);
		}
	}

	openDirections(isStartClient: boolean) {
		const clientId = this.clientService.getSelectedData();
		if (clientId) {
			this.directionsOpened.emit({clientDetails: {clientId, isStartClient}});
		}
	}
}
