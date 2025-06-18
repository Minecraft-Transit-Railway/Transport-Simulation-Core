import {Component, EventEmitter, Output} from "@angular/core";
import {RouteKeyService, RouteVariationService} from "../../service/route.service";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {RouteDisplayComponent} from "../route-display/route-display.component";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {ROUTE_TYPES} from "../../data/routeType";
import {TitleComponent} from "../title/title.component";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {TooltipModule} from "primeng/tooltip";
import {CheckboxModule} from "primeng/checkbox";
import {DividerModule} from "primeng/divider";
import {SelectModule} from "primeng/select";
import {FloatLabelModule} from "primeng/floatlabel";
import {FormsModule} from "@angular/forms";

@Component({
	selector: "app-route-panel",
	imports: [
		FloatLabelModule,
		SelectModule,
		CheckboxModule,
		DividerModule,
		TooltipModule,
		FormatNamePipe,
		FormatTimePipe,
		RouteDisplayComponent,
		DataListEntryComponent,
		TitleComponent,
		FormsModule,
	],
	templateUrl: "./route-panel.component.html",
	styleUrl: "./route-panel.component.css",
})
export class RoutePanelComponent {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>();
	protected dropdownValue?: { name: string; id: string; };

	constructor(private readonly routeVariationService: RouteVariationService, private readonly routeKeyService: RouteKeyService, private readonly formatTimePipe: FormatTimePipe) {
		routeKeyService.selectionChanged.subscribe(() => {
			this.dropdownValue = {name: Math.random().toString(), id: Math.random().toString()};
			setTimeout(() => {
				const dropdownRoutes = this.getDropdownRoutes();
				this.dropdownValue = dropdownRoutes ? dropdownRoutes[0] : undefined;
			}, 0);
		});
	}

	getDropdownRoutes() {
		return this.routeKeyService.getSelectedData()?.map(route => ({name: route.name.split("||")[1] ?? "(Untitled)", id: route.id}));
	}

	selectRoute(id: string) {
		this.routeVariationService.select(id);
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
		return route ? [...new Set(route.depots)].sort() : [];
	}

	getVehicleIcons(vehicles: { deviation: number, percentage: number }[], displayHeight: number) {
		const icon = this.getRouteIcon() ?? "";
		return vehicles.map(vehicle => ({icon, offset: vehicle.percentage * displayHeight / 2, tooltip: `${this.formatTimePipe.transform(Math.abs(Math.round(vehicle.deviation / 1000)), "")} ${SimplifyRoutesPipe.getDeviationString(true, vehicle.deviation)}`}));
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
