import {AfterViewInit, Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from "@angular/core";
import {NgOptimizedImage, NgTemplateOutlet} from "@angular/common";
import {RippleModule} from "primeng/ripple";
import {DomSanitizer, SafeHtml} from "@angular/platform-browser";

const iconHtmlCache = new Map<string, SafeHtml>();

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
export class DataListEntryComponent implements AfterViewInit, OnChanges {
	private readonly sanitizer = inject(DomSanitizer);

	@Input({required: true}) icons: string[] = [];
	@Input({required: true}) title: [string, string] = ["", ""];
	@Input({required: true}) subtitles: [string, string][] = [];
	@Input() color = "";
	@Input({required: true}) useLightColor = false;
	@Input({required: true}) clickable = true;
	@Output() entryClicked = new EventEmitter<void>();

	getIconHtml(icon: string): SafeHtml {
		if (!iconHtmlCache.has(icon)) {
			iconHtmlCache.set(icon, this.sanitizer.bypassSecurityTrustHtml(`<i class="iconify" data-icon="material-symbols:${icon}"></i>`));
		}
		return iconHtmlCache.get(icon)!;
	}

	private scanIconify() {
		setTimeout(() => {
			if ((window as unknown as { Iconify?: { scan?: () => void } }).Iconify?.scan) {
				(window as unknown as { Iconify: { scan: () => void } }).Iconify.scan();
			}
		}, 0);
	}

	ngAfterViewInit(): void {
		this.scanIconify();
	}

	ngOnChanges(changes: SimpleChanges): void {
		if (changes["icons"]) {
			this.scanIconify();
		}
	}
}
