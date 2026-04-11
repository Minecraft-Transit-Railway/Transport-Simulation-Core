import {Component, CUSTOM_ELEMENTS_SCHEMA, inject, Input} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {setCookie} from "../../data/utilities";
import {TooltipModule} from "primeng/tooltip";
import {SelectButtonChangeEvent, SelectButtonModule} from "primeng/selectbutton";
import {FormsModule} from "@angular/forms";
import {TranslocoDirective} from "@jsverse/transloco";

@Component({
	selector: "app-visibility-toggle",
	imports: [
		SelectButtonModule,
		TooltipModule,
		TranslocoDirective,
		FormsModule,
	],
	templateUrl: "./visibility-toggle.component.html",
	styleUrl: "./visibility-toggle.component.scss",
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class VisibilityToggleComponent {
	private readonly mapDataService = inject(MapDataService);

	@Input({required: true}) routeType = "";
	protected readonly visibilityOptions: { icon: string, value: "HIDDEN" | "SOLID" | "HOLLOW" | "DASHED", tooltip: string }[] = [
		{
			icon: "mdi:hide",
			value: "HIDDEN",
			tooltip: "visibility.hidden",
		},
		{
			icon: "fluent:solid",
			value: "SOLID",
			tooltip: "visibility.solid",
		},
		{
			icon: "fluent:hollow",
			value: "HOLLOW",
			tooltip: "visibility.hollow",
		},
		{
			icon: "fluent:dashed",
			value: "DASHED",
			tooltip: "visibility.dashed",
		},
	];

	getVisibility() {
		return this.mapDataService.routeTypeVisibility()[this.routeType];
	}

	setVisibility(event: SelectButtonChangeEvent) {
		this.mapDataService.routeTypeVisibility()[this.routeType] = event.value;
		this.mapDataService.updateData();
		Object.entries(this.mapDataService.routeTypeVisibility()).forEach(([newRouteTypeKey, visibility]) => setCookie(`visibility_${newRouteTypeKey}`, visibility));
	}
}
