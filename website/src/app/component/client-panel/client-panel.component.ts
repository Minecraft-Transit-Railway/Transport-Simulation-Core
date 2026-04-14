import {ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, inject, output} from "@angular/core";
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
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {TranslocoDirective} from "@jsverse/transloco";

@Component({
	selector: "app-client-panel",
	changeDetection: ChangeDetectionStrategy.OnPush,
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
		TranslocoDirective,
	],
	templateUrl: "./client-panel.component.html",
	styleUrl: "./client-panel.component.scss",
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ClientPanelComponent {
	private readonly clientService = inject(ClientService);
	private readonly mapDataService = inject(MapDataService);

	readonly stationClicked = output<string>();
	readonly routeClicked = output<string>();
	readonly directionsOpened = output<{ clientDetails: { clientId: string, isStartClient: boolean } }>();

	getName() {
		return this.clientService.client()?.name ?? "";
	}

	getImageSrc() {
		const clientId = this.clientService.selectedData();
		return clientId ? `https://mc-heads.net/avatar/${clientId}` : undefined;
	}

	getStation() {
		const client = this.clientService.client();
		return client ? client.station : undefined;
	}

	getRouteDetails() {
		const client = this.clientService.client();
		const route = client ? client.route : undefined;
		const routeStation1 = client ? client.routeStation1 : undefined;
		const routeStation2 = client ? client.routeStation2 : undefined;
		return route && routeStation1 && routeStation2 ? {route, routeStation1, routeStation2, icon: ROUTE_TYPES[route.type].icon} : undefined;
	}

	getRouteKey(route: { color: number, name: string, number: string }) {
		return SimplifyRoutesPipe.getRouteKey(route);
	}

	getCoordinatesText() {
		const client = this.clientService.client();
		return client ? `${Math.round(client.rawX)}, ${Math.round(client.rawZ)}` : "";
	}

	focus() {
		const clientId = this.clientService.selectedData();
		if (clientId) {
			this.mapDataService.animateClient.next(clientId);
		}
	}

	openDirections(isStartClient: boolean) {
		const clientId = this.clientService.selectedData();
		if (clientId) {
			this.directionsOpened.emit({clientDetails: {clientId, isStartClient}});
		}
	}
}
