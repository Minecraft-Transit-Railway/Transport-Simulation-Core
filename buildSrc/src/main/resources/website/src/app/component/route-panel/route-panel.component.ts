import {Component, EventEmitter, Output} from "@angular/core";
import {MatSelectModule} from "@angular/material/select";
import {RouteKeyService, RouteVariationService} from "../../service/route.service";
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
	@Output() directionsOpened = new EventEmitter<void>();
	protected dropdownValue = "";

	constructor(private readonly routeVariationService: RouteVariationService, private readonly routeKeyService: RouteKeyService, private readonly formatTimePipe: FormatTimePipe) {
		routeKeyService.selectionChanged.subscribe(() => {
			this.dropdownValue = Math.random().toString();
			setTimeout(() => this.dropdownValue = "id_0", 0);
		});
	}

	getNames() {
		return this.routeVariationService.getNames().map(name => name.split("||")[1] ?? "(Untitled)");
	}

	selectRoute(id: string) {
		const routeVariations = this.routeKeyService.getSelectedData();
		if (routeVariations) {
			this.routeVariationService.select(routeVariations[parseInt(id.split("_")[1])].id);
		}
	}

	getRouteName() {
		const route = this.routeVariationService.getSelectedData();
		return route ? route.name.split("||")[0] : "";
	}

	getRouteColor() {
		const route = this.routeVariationService.getSelectedData();
		return route ? route.color : undefined;
	}

	getRouteIcon() {
		const route = this.routeVariationService.getSelectedData();
		return route ? ROUTE_TYPES[route.type].icon : undefined;
	}

	getRouteDepots() {
		const route = this.routeVariationService.getSelectedData();
		return route ? route.depots : [];
	}

	getVehicleIcons(vehicles: { deviation: number, percentage: number }[], displayHeight: number) {
		const icon = this.getRouteIcon() ?? "";
		return vehicles.map(vehicle => ({icon, offset: vehicle.percentage * displayHeight / 2, tooltip: vehicle.deviation ? `${this.formatTimePipe.transform(Math.abs(Math.round(vehicle.deviation / 1000)), "")} ${SimplifyRoutesPipe.getDeviationString(true, vehicle.deviation)}` : undefined}));
	}

	getRouteStationDetails() {
		return this.routeVariationService.routeStationDetails;
	}

	getTotalDurationSeconds() {
		return this.routeVariationService.getTotalDurationSeconds();
	}

	hasDurations() {
		return this.routeVariationService.routeStationDetails[0]?.durationSeconds;
	}

	hasDwellTimes() {
		return this.routeVariationService.routeStationDetails[0]?.dwellTimeSeconds;
	}
}
