import {Component, inject} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {setCookie} from "../../data/utilities";
import {TooltipModule} from "primeng/tooltip";
import {SelectButtonChangeEvent, SelectButtonModule} from "primeng/selectbutton";
import {FormsModule} from "@angular/forms";

@Component({
	selector: "app-interchange-style-toggle",
	imports: [
		SelectButtonModule,
		TooltipModule,
		FormsModule,
	],
	templateUrl: "./interchange-style-toggle.component.html",
	styleUrl: "./interchange-style-toggle.component.css",
})
export class InterchangeStyleToggleComponent {
	private readonly mapDataService = inject(MapDataService);

	protected readonly interchangeStyleOptions: { icon: string, value: "DOTTED" | "HOLLOW", tooltip: string }[] = [
		{
			icon: "more_horiz",
			value: "DOTTED",
			tooltip: "Dotted",
		},
		{
			icon: "drag_handle",
			value: "HOLLOW",
			tooltip: "Hollow",
		},
	];

	getInterchangeStyle() {
		return this.mapDataService.interchangeStyle;
	}

	setInterchangeStyle(event: SelectButtonChangeEvent) {
		this.mapDataService.interchangeStyle = event.value;
		this.mapDataService.updateData();
		setCookie("interchange_style", event.value);
	}
}
