import {ChangeDetectionStrategy, Component, input} from "@angular/core";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {FormatColorPipe} from "../../pipe/formatColorPipe";

@Component({
	selector: "app-title",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		SplitNamePipe,
		FormatColorPipe,
	],
	templateUrl: "./title.component.html",
	styleUrl: "./title.component.scss",
})
export class TitleComponent {
	readonly name = input.required<string>();
	readonly color = input<number>();
}
