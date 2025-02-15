import {Component, EventEmitter, Output} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {MatButtonToggleModule} from "@angular/material/button-toggle";
import {ROUTE_TYPES, RouteType} from "../../data/routeType";
import {ReactiveFormsModule} from "@angular/forms";
import {MatIconModule} from "@angular/material/icon";
import {MatSelectModule} from "@angular/material/select";
import {DimensionService} from "../../service/dimension.service";
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatSlideToggleModule} from "@angular/material/slide-toggle";
import {ThemeService} from "../../service/theme.service";
import {setCookie} from "../../data/utilities";
import {MatDividerModule} from "@angular/material/divider";
import {MatButtonModule} from "@angular/material/button";

@Component({
	selector: "app-main-panel",
	imports: [
		MatButtonToggleModule,
		ReactiveFormsModule,
		MatIconModule,
		MatSelectModule,
		MatTooltipModule,
		MatSlideToggleModule,
		MatDividerModule,
		MatButtonModule,
	],
	templateUrl: "./main-panel.component.html",
	styleUrl: "./main-panel.component.css",
})
export class MainPanelComponent {
	@Output() directionsOpened = new EventEmitter<void>;
	protected dropdownValue = "";
	protected readonly routeTypes: [string, RouteType][] = [];

	constructor(private readonly mapDataService: MapDataService, private readonly dimensionService: DimensionService, private readonly themeService: ThemeService) {
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

	setVisibility(routeTypeKey: string, value: string) {
		this.mapDataService.routeTypeVisibility[routeTypeKey] = value as "HIDDEN" | "SOLID" | "HOLLOW";
		this.mapDataService.updateData();
		Object.entries(this.mapDataService.routeTypeVisibility).forEach(([newRouteTypeKey, visibility]) => setCookie(`visibility_${newRouteTypeKey}`, visibility));
	}

	getVisibility(routeTypeKey: string): "HIDDEN" | "SOLID" | "HOLLOW" {
		return this.mapDataService.routeTypeVisibility[routeTypeKey];
	}

	setInterchangeStyle(value: string) {
		this.mapDataService.interchangeStyle = value as "DOTTED" | "HOLLOW";
		this.mapDataService.drawMap.emit();
		setCookie("interchange_style", value);
	}

	hasInterchanges() {
		return this.mapDataService.stationConnections.length > 0;
	}

	getInterchangeStyle(): "DOTTED" | "HOLLOW" {
		return this.mapDataService.interchangeStyle;
	}

	getDimensions() {
		return this.dimensionService.getDimensions();
	}

	setDimension(dimension: string) {
		this.mapDataService.setDimension(dimension);
	}

	changeTheme(isDarkTheme: boolean) {
		this.themeService.setTheme(isDarkTheme);
	}

	isDarkTheme() {
		return this.themeService.isDarkTheme();
	}
}
