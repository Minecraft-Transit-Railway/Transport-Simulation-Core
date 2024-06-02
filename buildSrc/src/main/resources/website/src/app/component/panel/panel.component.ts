import {Component} from "@angular/core";
import {MatExpansionPanel, MatExpansionPanelDescription, MatExpansionPanelHeader, MatExpansionPanelTitle} from "@angular/material/expansion";
import {DataService} from "../../service/data.service";
import {NgForOf, NgIf} from "@angular/common";
import {MatButtonToggle, MatButtonToggleGroup} from "@angular/material/button-toggle";
import {ROUTE_TYPES, RouteType} from "../../data/routeType";
import {ReactiveFormsModule} from "@angular/forms";
import {MatIcon} from "@angular/material/icon";
import {MatFormField, MatLabel} from "@angular/material/form-field";
import {MatOption, MatSelect} from "@angular/material/select";
import {DimensionService} from "../../service/dimension.service";

@Component({
	selector: "app-panel",
	standalone: true,
	imports: [
		MatExpansionPanel,
		MatExpansionPanelTitle,
		MatExpansionPanelHeader,
		MatExpansionPanelDescription,
		NgForOf,
		MatButtonToggleGroup,
		MatButtonToggle,
		ReactiveFormsModule,
		NgIf,
		MatIcon,
		MatLabel,
		MatFormField,
		MatSelect,
		MatOption,
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
