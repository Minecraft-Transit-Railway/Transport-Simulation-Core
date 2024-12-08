import {Component, EventEmitter, Output} from "@angular/core";
import {MatSelectModule} from "@angular/material/select";
import {RouteService} from "../../service/route.service";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {RouteDisplayComponent} from "../route-display/route-display.component";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";

@Component({
	selector: "app-route-panel",
	standalone: true,
	imports: [
		MatSelectModule,
		FormatNamePipe,
		SplitNamePipe,
		RouteDisplayComponent,
		DataListEntryComponent,
	],
	templateUrl: "./route-panel.component.html",
	styleUrl: "./route-panel.component.css",
})
export class RoutePanelComponent {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>;

	constructor(private readonly routeService: RouteService) {
	}

	getNames() {
		return this.routeService.getNames().map(name => name.split("||")[1] ?? "(Untitled)");
	}

	getRandomSeed() {
		return this.routeService.getRandomSeed();
	}

	selectRoute(id: string) {
		this.routeService.selectRoute(parseInt(id.split("_")[1]));
	}

	getRouteName() {
		const route = this.routeService.getSelectedRoute();
		return route ? route.name.split("||")[0] : "";
	}

	getRouteColor() {
		const route = this.routeService.getSelectedRoute();
		return route ? parseInt(route.color, 16) : undefined;
	}

	getStations() {
		const route = this.routeService.getSelectedRoute();
		return route ? route.stations : [];
	}
}
