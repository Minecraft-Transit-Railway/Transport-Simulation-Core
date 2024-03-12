import {Component} from "@angular/core";
import {MatExpansionPanel, MatExpansionPanelDescription, MatExpansionPanelHeader, MatExpansionPanelTitle} from "@angular/material/expansion";
import {DataService} from "../../service/data.service";
import {NgForOf, NgIf} from "@angular/common";
import {MatButtonToggle, MatButtonToggleGroup} from "@angular/material/button-toggle";
import {ROUTE_TYPES, RouteType} from "../../data/routeType";
import {ReactiveFormsModule} from "@angular/forms";
import {MatIcon} from "@angular/material/icon";

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
		MatIcon
	],
	templateUrl: "./panel.component.html",
	styleUrl: "./panel.component.css"
})
export class PanelComponent {

	readonly routeTypes: [string, RouteType][] = Object.entries(ROUTE_TYPES).map(([routeTypeKey, routeType]) => [routeTypeKey, routeType]);

	constructor(private readonly dataService: DataService) {
	}

	onSelect(routeTypeKey: string, value: string) {
		this.dataService.getRouteTypes()[routeTypeKey] = parseInt(value);
		this.dataService.updateData();
	}

	getSelected(routeTypeKey: string): number | undefined {
		return this.dataService.getRouteTypes()[routeTypeKey];
	}
}
