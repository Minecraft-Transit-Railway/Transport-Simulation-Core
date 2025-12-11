import {Component, EventEmitter, inject, Output, signal} from "@angular/core";
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
	styleUrl: "./route-panel.component.scss",
})
export class RoutePanelComponent {
	private readonly routeVariationService = inject(RouteVariationService);
	private readonly routeKeyService = inject(RouteKeyService);
	private readonly formatTimePipe = inject(FormatTimePipe);

	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>();
	protected dropdownValue = signal<{ name: string; id: string; } | undefined>(undefined);

	constructor() {
		this.routeKeyService.selectionChanged.subscribe(() => {
			this.dropdownValue.set({name: Math.random().toString(), id: Math.random().toString()});
			setTimeout(() => {
				const dropdownRoutes = this.getDropdownRoutes();
				this.dropdownValue.set(dropdownRoutes ? dropdownRoutes[0] : undefined);
			}, 0);
		});
	}

	getDropdownRoutes() {
		return this.routeKeyService.selectedData()?.map(route => ({name: route.name.split("||")[1] ?? "(Untitled)", id: route.id}));
	}

	selectRoute(id: string) {
		this.routeVariationService.select(id);
	}

	getRouteName() {
		const route = this.routeVariationService.selectedData();
		return route ? route.name.split("||")[0] : "";
	}

	getRouteColor() {
		const route = this.routeVariationService.selectedData();
		return route ? route.color : undefined;
	}

	getRouteIcon() {
		const route = this.routeVariationService.selectedData();
		return route ? ROUTE_TYPES[route.type].icon : undefined;
	}

	getRouteDepots() {
		const route = this.routeVariationService.selectedData();
		return route ? [...new Set(route.depots)].sort() : [];
	}

	getVehicleIcons(index: number, displayHeight: number) {
		const icon = this.getRouteIcon() ?? "";
		const maxIndex = this.routeVariationService.routeStationDetails().length - 1;
		return this.routeVariationService.routeVehicles()[index].map(vehicle => {
			const offset = vehicle.percentage * displayHeight / 2;
			return {
				icon,
				offset: index === 0 ? Math.max(0, offset) : index === maxIndex ? Math.min(offset, 0) : offset,
				tooltip: `${this.formatTimePipe.transform(Math.abs(Math.round(vehicle.deviation / 1000)), "")} ${SimplifyRoutesPipe.getDeviationString(true, vehicle.deviation)}`,
			};
		});
	}

	getRouteStationDetails() {
		return this.routeVariationService.routeStationDetails();
	}

	getTotalDurationSeconds() {
		return this.routeVariationService.getTotalDurationSeconds();
	}

	hasDurations() {
		return this.routeVariationService.routeStationDetails()[0]?.durationSeconds;
	}

	hasDwellTimes() {
		return this.routeVariationService.routeStationDetails()[0]?.dwellTimeSeconds;
	}
}
