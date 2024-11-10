import {Component} from "@angular/core";
import {DataService} from "../../service/data.service";
import {MatButtonToggleModule} from "@angular/material/button-toggle";
import {ROUTE_TYPES, RouteType} from "../../data/routeType";
import {ReactiveFormsModule} from "@angular/forms";
import {MatIconModule} from "@angular/material/icon";
import {MatSelectModule} from "@angular/material/select";
import {DimensionService} from "../../service/dimension.service";
import {SearchComponent} from "../search/search.component";
import {MatTooltipModule} from "@angular/material/tooltip";

@Component({
	selector: "app-panel",
	standalone: true,
	imports: [
		MatButtonToggleModule,
		ReactiveFormsModule,
		MatIconModule,
		MatSelectModule,
		MatTooltipModule,
		SearchComponent,
	],
	templateUrl: "./panel.component.html",
	styleUrl: "./panel.component.css",
})
export class PanelComponent {

	readonly routeTypes: [string, RouteType][] = Object.entries(ROUTE_TYPES).map(([routeTypeKey, routeType]) => [routeTypeKey, routeType]);

	constructor(private readonly dataService: DataService, private readonly dimensionService: DimensionService) {
	}

	onSelect(routeTypeKey: string, value: string) {
		this.dataService.getRouteTypes()[routeTypeKey] = parseInt(value);
		this.dataService.updateData();
	}

	getSelected(routeTypeKey: string): number | undefined {
		return this.dataService.getRouteTypes()[routeTypeKey];
	}

	getDimensions() {
		return this.dimensionService.getDimensions();
	}

	setDimension(dimension: string) {
		this.dataService.setDimension(dimension);
	}
}
