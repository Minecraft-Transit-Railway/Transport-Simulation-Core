import {Component} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {MatButtonToggleModule} from "@angular/material/button-toggle";
import {ROUTE_TYPES, RouteType} from "../../data/routeType";
import {ReactiveFormsModule} from "@angular/forms";
import {MatIconModule} from "@angular/material/icon";
import {MatSelectModule} from "@angular/material/select";
import {DimensionService} from "../../service/dimension.service";
import {MatTooltipModule} from "@angular/material/tooltip";

@Component({
	selector: "app-main-panel",
	standalone: true,
	imports: [
		MatButtonToggleModule,
		ReactiveFormsModule,
		MatIconModule,
		MatSelectModule,
		MatTooltipModule,
	],
	templateUrl: "./main-panel.component.html",
	styleUrl: "./main-panel.component.css",
})
export class MainPanelComponent {
	protected dropdownValue = "";
	protected readonly routeTypes: [string, RouteType][] = [];

	constructor(private readonly mapDataService: MapDataService, private readonly dimensionService: DimensionService) {
		mapDataService.dataProcessed.subscribe(() => {
			if (!this.dropdownValue) {
				this.dropdownValue = dimensionService.getDimensions()[0];
			}
			this.routeTypes.length = 0;
			Object.entries(ROUTE_TYPES).forEach(([routeTypeKey, routeType]) => {
				if (routeTypeKey in mapDataService.routeTypeVisibility) {
					this.routeTypes.push([routeTypeKey, routeType]);
				}
			});
		});
	}

	onSelect(routeTypeKey: string, value: string) {
		this.mapDataService.routeTypeVisibility[routeTypeKey] = value as "HIDDEN" | "SOLID" | "HOLLOW";
		this.mapDataService.updateData();
	}

	getSelected(routeTypeKey: string): string | undefined {
		return this.mapDataService.routeTypeVisibility[routeTypeKey];
	}

	getDimensions() {
		return this.dimensionService.getDimensions();
	}

	setDimension(dimension: string) {
		this.mapDataService.setDimension(dimension);
	}
}
