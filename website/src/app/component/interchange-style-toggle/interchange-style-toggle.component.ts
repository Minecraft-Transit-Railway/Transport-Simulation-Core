import {Component, CUSTOM_ELEMENTS_SCHEMA, inject} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {setCookie} from "../../data/utilities";
import {TooltipModule} from "primeng/tooltip";
import {SelectButtonChangeEvent, SelectButtonModule} from "primeng/selectbutton";
import {FormsModule} from "@angular/forms";
import {TranslocoDirective} from "@jsverse/transloco";

@Component({
	selector: "app-interchange-style-toggle",
	imports: [
		SelectButtonModule,
		TooltipModule,
		TranslocoDirective,
		FormsModule,
	],
	templateUrl: "./interchange-style-toggle.component.html",
	styleUrl: "./interchange-style-toggle.component.scss",
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class InterchangeStyleToggleComponent {
	private readonly mapDataService = inject(MapDataService);

	protected readonly interchangeStyleOptions: { icon: string, value: "DOTTED" | "HOLLOW", tooltip: string }[] = [
		{
			icon: "more-horiz",
			value: "DOTTED",
			tooltip: "visibility.dotted",
		},
		{
			icon: "drag-handle",
			value: "HOLLOW",
			tooltip: "visibility.hollow",
		},
	];

	getInterchangeStyle() {
		return this.mapDataService.interchangeStyle();
	}

	setInterchangeStyle(event: SelectButtonChangeEvent) {
		this.mapDataService.interchangeStyle.set(event.value);
		this.mapDataService.updateData();
		setCookie("interchange_style", event.value);
	}
}
