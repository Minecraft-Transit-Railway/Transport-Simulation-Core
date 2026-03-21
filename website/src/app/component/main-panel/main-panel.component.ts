import {Component, EventEmitter, inject, Output, AfterViewInit} from "@angular/core";
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
import {AccordionModule} from "primeng/accordion";
import {ClientsService} from "../../service/clients.service";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {DomSanitizer, SafeHtml} from "@angular/platform-browser";

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
		AccordionModule,
		FormsModule,
		ReactiveFormsModule,
		SearchComponent,
		VisibilityToggleComponent,
		InterchangeStyleToggleComponent,
		DataListEntryComponent,

	],
	templateUrl: "./main-panel.component.html",
	styleUrl: "./main-panel.component.scss",
})
export class MainPanelComponent implements AfterViewInit {
	private readonly mapDataService = inject(MapDataService);
	private readonly dimensionService = inject(DimensionService);
	private readonly clientsService = inject(ClientsService);
	private readonly themeService = inject(ThemeService);
	private readonly sanitizer = inject(DomSanitizer);
	private readonly iconCache = new Map<string, SafeHtml>();

	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() clientClicked = new EventEmitter<string>();
	@Output() directionsOpened = new EventEmitter<void>();

	protected readonly formGroup = new FormGroup({
		search: new FormControl(""),
		dimension: new FormControl(""),
		dimension1: new FormControl<"HIDDEN" | "SOLID" | "HOLLOW" | "DASHED">("HIDDEN"),
		themeToggle: new FormControl(this.themeService.isDarkTheme()),
	});
	protected readonly routeTypes: [string, RouteType][] = [];

	constructor() {
		this.mapDataService.dataProcessed.subscribe(() => {
			if (!this.formGroup.getRawValue().dimension) {
				this.formGroup.patchValue({dimension: this.dimensionService.getDimensions()[0]});
			}
			this.routeTypes.length = 0;
			Object.entries(ROUTE_TYPES).forEach(([routeTypeKey, routeType]) => {
				if (routeTypeKey in this.mapDataService.routeTypeVisibility()) {
					this.routeTypes.push([routeTypeKey, routeType]);
				}
			});
			// Trigger Iconify to rebuild with new icons
			setTimeout(() => {
				if ((window as unknown as { Iconify?: { build?: () => void } }).Iconify?.build) {
					(window as unknown as { Iconify: { build: () => void } }).Iconify.build();
				}
			}, 0);
		});
	}

	ngAfterViewInit(): void {
		// Trigger Iconify to rebuild with all icons
		setTimeout(() => {
			if ((window as unknown as { Iconify?: { build?: () => void } }).Iconify?.build) {
				(window as unknown as { Iconify: { build: () => void } }).Iconify.build();
			}
		}, 0);
	}

	hasInterchanges() {
		return this.mapDataService.stationConnections().length > 0;
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

	getAllClients() {
		return this.clientsService.allClients();
	}

	clickStation(id: string) {
		this.stationClicked.emit(id);
		this.formGroup.patchValue({search: undefined});
	}

	clickRoute(id: string) {
		this.routeClicked.emit(id);
		this.formGroup.patchValue({search: undefined});
	}

	clickClient(id: string) {
		this.clientClicked.emit(id);
		this.formGroup.patchValue({search: undefined});
	}

	trackByRouteType(index: number, item: [string, RouteType]): string {
		return item[0];
	}

	getRouteIcon(icon: string): SafeHtml {
		if (!this.iconCache.has(icon)) {
			const iconHtml = `<i class="iconify" data-icon="material-symbols:${icon}"></i>`;
			this.iconCache.set(icon, this.sanitizer.bypassSecurityTrustHtml(iconHtml));
		}
		return this.iconCache.get(icon)!;
	}

	changeTheme(isDarkTheme: boolean) {
		this.themeService.setTheme(isDarkTheme);
	}
}
