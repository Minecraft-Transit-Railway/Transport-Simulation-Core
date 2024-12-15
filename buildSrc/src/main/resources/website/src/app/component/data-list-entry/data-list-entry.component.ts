import {Component, EventEmitter, Input, Output} from "@angular/core";
import {MatIconModule} from "@angular/material/icon";
import {MatRipple} from "@angular/material/core";
import {NgTemplateOutlet} from "@angular/common";

@Component({
	selector: "app-data-list-entry",
	imports: [
		MatIconModule,
		MatRipple,
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
