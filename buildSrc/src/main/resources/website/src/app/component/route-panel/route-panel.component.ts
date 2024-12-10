import {Component, EventEmitter, Output} from "@angular/core";
import {MatSelectModule} from "@angular/material/select";
import {RouteService} from "../../service/route.service";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {RouteDisplayComponent} from "../route-display/route-display.component";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {MatIconModule} from "@angular/material/icon";
import {MatTooltipModule} from "@angular/material/tooltip";
import {ROUTE_TYPES} from "../../data/routeType";
import {MatCheckbox} from "@angular/material/checkbox";
import {TitleComponent} from "../title/title.component";
import {MatDividerModule} from "@angular/material/divider";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";

@Component({
	selector: "app-route-panel",
	standalone: true,
	imports: [
		MatSelectModule,
		FormatNamePipe,
		RouteDisplayComponent,
		DataListEntryComponent,
		FormatTimePipe,
		MatIconModule,
		MatTooltipModule,
		MatCheckbox,
		TitleComponent,
		MatDividerModule,
	],
	templateUrl: "./route-panel.component.html",
	styleUrl: "./route-panel.component.css",
})
export class RoutePanelComponent {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>;

	constructor(private readonly routeService: RouteService, private readonly formatTimePipe: FormatTimePipe) {
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

	getRouteIcon() {
		const route = this.routeService.getSelectedRoute();
		return route ? ROUTE_TYPES[route.type].icon : undefined;
	}

	getRouteDepots() {
		const route = this.routeService.getSelectedRoute();
		return route ? route.depots : [];
	}

	getVehicleIcons(vehicles: { deviation: number, percentage: number }[], displayHeight: number) {
		const icon = this.getRouteIcon() ?? "";
		return vehicles.map(vehicle => ({icon, offset: vehicle.percentage * displayHeight / 2, tooltip: vehicle.deviation ? `${this.formatTimePipe.transform(Math.abs(Math.round(vehicle.deviation / 1000)), "")} ${SimplifyRoutesPipe.getDeviationString(true, vehicle.deviation)}` : undefined}));
	}

	getRouteStationDetails() {
		return this.routeService.getRouteStationDetails();
	}

	getTotalDurationSeconds() {
		return this.routeService.getTotalDurationSeconds();
	}

	hasDurations() {
		return this.routeService.getRouteStationDetails()[0]?.durationSeconds;
	}

	hasDwellTimes() {
		return this.routeService.getRouteStationDetails()[0]?.dwellTimeSeconds;
	}
}
