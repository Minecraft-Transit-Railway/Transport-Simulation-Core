import {ChangeDetectionStrategy, Component, computed, CUSTOM_ELEMENTS_SCHEMA, inject, output, signal} from "@angular/core";
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
import {TranslocoDirective, TranslocoService} from "@jsverse/transloco";
import {getCookie, setCookie} from "../../data/utilities";
import {VERSION} from "../../../version";

const languageMapping: Record<string, string> = {
	en: "English",
	zh: "繁體中文",
};

@Component({
	selector: "app-main-panel",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		FloatLabelModule,
		SelectModule,
		SelectButtonModule,
		ButtonModule,
		ToggleSwitchModule,
		DividerModule,
		TooltipModule,
		AccordionModule,
		TranslocoDirective,
		FormsModule,
		ReactiveFormsModule,
		SearchComponent,
		VisibilityToggleComponent,
		InterchangeStyleToggleComponent,
		DataListEntryComponent,
	],
	templateUrl: "./main-panel.component.html",
	styleUrl: "./main-panel.component.scss",
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class MainPanelComponent {
	private readonly mapDataService = inject(MapDataService);
	private readonly dimensionService = inject(DimensionService);
	private readonly clientsService = inject(ClientsService);
	private readonly themeService = inject(ThemeService);
	private readonly translocoService = inject(TranslocoService);

	readonly stationClicked = output<string>();
	readonly routeClicked = output<string>();
	readonly clientClicked = output<string>();
	readonly directionsOpened = output<void>();
	readonly version = VERSION;

	protected readonly formGroup = new FormGroup({
		search: new FormControl(""),
		dimension: new FormControl(""),
		dimension1: new FormControl<"HIDDEN" | "SOLID" | "HOLLOW" | "DASHED">("HIDDEN"),
		themeToggle: new FormControl(this.themeService.isDarkTheme()),
		language: new FormControl(getCookie("language") || this.translocoService.getActiveLang()),
	});
	protected readonly routeTypes = signal<[string, RouteType][]>([]);

	protected readonly languageOptions = computed(() => {
		return this.translocoService.getAvailableLangs().map(lang => {
			const code = typeof lang === "string" ? lang : lang.id;
			return {value: code, label: languageMapping[code]};
		});
	});

	constructor() {
		this.mapDataService.dataProcessed.subscribe(() => {
			if (!this.formGroup.getRawValue().dimension) {
				this.formGroup.patchValue({dimension: this.dimensionService.getDimensions()[0]});
			}
			const newRouteTypes: [string, RouteType][] = [];
			Object.entries(ROUTE_TYPES).forEach(([routeTypeKey, routeType]) => {
				if (routeTypeKey in this.mapDataService.routeTypeVisibility()) {
					newRouteTypes.push([routeTypeKey, routeType]);
				}
			});
			this.routeTypes.set(newRouteTypes);
		});
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

	changeTheme(isDarkTheme: boolean) {
		this.themeService.setTheme(isDarkTheme);
	}

	changeLanguage(lang: string) {
		setCookie("language", lang);
		this.translocoService.setActiveLang(lang);
	}
}
