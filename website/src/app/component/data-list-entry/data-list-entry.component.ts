import {AfterViewInit, Component, EventEmitter, Input, Output} from "@angular/core";
import {NgOptimizedImage, NgTemplateOutlet} from "@angular/common";
import {RippleModule} from "primeng/ripple";

@Component({
	selector: "app-data-list-entry",
	imports: [
		RippleModule,
		NgTemplateOutlet,
		NgOptimizedImage,
	],
	templateUrl: "./data-list-entry.component.html",
	styleUrl: "./data-list-entry.component.scss",
})
export class DataListEntryComponent implements AfterViewInit {
	@Input({required: true}) icons: string[] = [];
	@Input({required: true}) title: [string, string] = ["", ""];
	@Input({required: true}) subtitles: [string, string][] = [];
	@Input() color = "";
	@Input({required: true}) useLightColor = false;
	@Input({required: true}) clickable = true;
	@Output() entryClicked = new EventEmitter<void>();

	ngAfterViewInit(): void {
		setTimeout(() => {
			if ((window as unknown as { Iconify?: { scan?: () => void } }).Iconify?.scan) {
				(window as unknown as { Iconify: { scan: () => void } }).Iconify.scan();
			}
		}, 0);
	}
}
