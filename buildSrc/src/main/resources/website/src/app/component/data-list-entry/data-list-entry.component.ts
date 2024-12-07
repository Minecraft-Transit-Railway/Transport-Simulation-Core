import {Component, EventEmitter, Input, Output} from "@angular/core";
import {MatIconModule} from "@angular/material/icon";
import {MatRipple} from "@angular/material/core";

@Component({
	selector: "app-data-list-entry",
	standalone: true,
	imports: [
		MatIconModule,
		MatRipple,
	],
	templateUrl: "./data-list-entry.component.html",
	styleUrl: "./data-list-entry.component.css",
})
export class DataListEntryComponent {
	@Input({required: true}) icons: string[] = [];
	@Input({required: true}) title: [string, string] = ["", ""];
	@Input({required: true}) subtitles: [string, string][] = [];
	@Input({required: true}) color = "";
	@Input({required: true}) useLightColor = false;
	@Output() entryClicked = new EventEmitter<void>;
}
