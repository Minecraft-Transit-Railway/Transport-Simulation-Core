import {Component, EventEmitter, Output} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {ROUTE_TYPES, RouteType} from "../../data/routeType";
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule} from "@angular/forms";
import {DimensionService} from "../../service/dimension.service";
import {ThemeService} from "../../service/theme.service";
import {FloatLabelModule} from "primeng/floatlabel";
import {SelectModule} from "primeng/select";
import {SelectButtonModule} from "primeng/selectbutton";
import {TooltipModule} from "primeng/tooltip";
import {DividerModule} from "primeng/divider";
import {ButtonModule} from "primeng/button";
import {ToggleSwitchModule} from "primeng/toggleswitch";
import {VisibilityToggleComponent} from "../visibility-toggle/visibility-toggle.component";
import {InterchangeStyleToggleComponent} from "../interchange-style-toggle/interchange-style-toggle.component";
import {SearchComponent} from "../search/search.component";

@Component({
	selector: "app-main-panel",
	imports: [
		FloatLabelModule,
		SelectModule,
		SelectButtonModule,
		ButtonModule,
		ToggleSwitchModule,
		DividerModule,
		TooltipModule,
		FormsModule,
		ReactiveFormsModule,
		SearchComponent,
		VisibilityToggleComponent,
		InterchangeStyleToggleComponent,
	],
	templateUrl: "./main-panel.component.html",
	styleUrl: "./main-panel.component.css",
})
export class MainPanelComponent {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>();

	protected readonly formGroup = new FormGroup({
		search: new FormControl(""),
		dimension: new FormControl(""),
		dimension1: new FormControl<"HIDDEN" | "SOLID" | "HOLLOW" | "DASHED">("HIDDEN"),
		themeToggle: new FormControl(this.themeService.isDarkTheme()),
	});
	protected readonly routeTypes: [string, RouteType][] = [];

	constructor(private readonly mapDataService: MapDataService, private readonly dimensionService: DimensionService, private readonly themeService: ThemeService) {
		mapDataService.dataProcessed.subscribe(() => {
			if (!this.formGroup.getRawValue().dimension) {
				this.formGroup.patchValue({dimension: dimensionService.getDimensions()[0]});
			}
			this.routeTypes.length = 0;
			Object.entries(ROUTE_TYPES).forEach(([routeTypeKey, routeType]) => {
				if (routeTypeKey in mapDataService.routeTypeVisibility) {
					this.routeTypes.push([routeTypeKey, routeType]);
				}
			});
		});
	}

	hasInterchanges() {
		return this.mapDataService.stationConnections.length > 0;
	}

	getDimensions() {
		return this.dimensionService.getDimensions();
	}

	setDimension() {
		const data = this.formGroup.getRawValue();
		if (data.dimension) {
			this.mapDataService.setDimension(data.dimension);
		}
	}

	clickStation(id: string) {
		this.stationClicked.emit(id);
		this.formGroup.patchValue({search: undefined});
	}

	clickRoute(id: string) {
		this.routeClicked.emit(id);
		this.formGroup.patchValue({search: undefined});
	}

	changeTheme(isDarkTheme: boolean) {
		this.themeService.setTheme(isDarkTheme);
	}
}
