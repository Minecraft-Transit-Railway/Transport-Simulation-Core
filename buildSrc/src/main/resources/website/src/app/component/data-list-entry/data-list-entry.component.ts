import {Component, EventEmitter, Input, Output} from "@angular/core";
import {NgTemplateOutlet} from "@angular/common";
import {RippleModule} from "primeng/ripple";

@Component({
	selector: "app-data-list-entry",
	imports: [
		RippleModule,
		NgTemplateOutlet,
	],
	templateUrl: "./data-list-entry.component.html",
	styleUrl: "./data-list-entry.component.css",
})
export class DataListEntryComponent {
	@Input({required: true}) icons: string[] = [];
	@Input({required: true}) title: [string, string] = ["", ""];
	@Input({required: true}) subtitles: [string, string][] = [];
	@Input() color = "";
	@Input({required: true}) useLightColor = false;
	@Input({required: true}) clickable = true;
	@Output() entryClicked = new EventEmitter<void>();
}
