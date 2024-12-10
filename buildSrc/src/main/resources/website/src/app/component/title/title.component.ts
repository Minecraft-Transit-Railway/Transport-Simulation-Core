import {Component, Input} from "@angular/core";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {FormatColorPipe} from "../../pipe/formatColorPipe";

@Component({
	selector: "app-title",
	standalone: true,
	imports: [
		SplitNamePipe,
		FormatColorPipe,
	],
	templateUrl: "./title.component.html",
	styleUrl: "./title.component.css",
})
export class TitleComponent {
	@Input({required: true}) name: string = "";
	@Input({required: true}) color?: number;
}
