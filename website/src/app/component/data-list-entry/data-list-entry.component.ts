import {ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, input, output} from "@angular/core";
import {NgOptimizedImage, NgTemplateOutlet} from "@angular/common";
import {RippleModule} from "primeng/ripple";

@Component({
	selector: "app-data-list-entry",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		RippleModule,
		NgTemplateOutlet,
		NgOptimizedImage,
	],
	templateUrl: "./data-list-entry.component.html",
	styleUrl: "./data-list-entry.component.scss",
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class DataListEntryComponent {
	readonly icons = input.required<string[]>();
	readonly title = input.required<[string, string]>();
	readonly subtitles = input.required<[string, string][]>();
	readonly color = input("");
	readonly useLightColor = input.required<boolean>();
	readonly clickable = input.required<boolean>();
	readonly entryClicked = output<void>();
}
