import {Component} from "@angular/core";
import {MatExpansionPanel, MatExpansionPanelDescription, MatExpansionPanelHeader, MatExpansionPanelTitle} from "@angular/material/expansion";
import {DataService} from "../../service/data.service";
import {NgForOf} from "@angular/common";
import {MatButtonToggle, MatButtonToggleGroup} from "@angular/material/button-toggle";
import {ROUTE_TYPES} from "../../data/routeType";

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
		MatButtonToggle
	],
	templateUrl: "./panel.component.html",
	styleUrl: "./panel.component.css"
})
export class PanelComponent {

	constructor(private readonly dataService: DataService) {
	}

	getRouteTypes() {
		return Object.entries(this.dataService.getRouteTypes()).map(([routeType, state]) => [ROUTE_TYPES[routeType], state]);
	}
}
