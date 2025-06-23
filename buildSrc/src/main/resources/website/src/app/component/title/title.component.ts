import {Component, Input} from "@angular/core";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {FormatColorPipe} from "../../pipe/formatColorPipe";

@Component({
	selector: "app-title",
	imports: [
		SplitNamePipe,
		FormatColorPipe,
	],
	templateUrl: "./title.component.html",
	styleUrl: "./title.component.css",
})
export class TitleComponent {
	@Input({required: true}) name = "";
	@Input({required: true}) color?: number;
}
