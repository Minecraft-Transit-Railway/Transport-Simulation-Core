import {Component, inject, AfterViewInit} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {setCookie} from "../../data/utilities";
import {TooltipModule} from "primeng/tooltip";
import {SelectButtonChangeEvent, SelectButtonModule} from "primeng/selectbutton";
import {FormsModule} from "@angular/forms";
import {DomSanitizer, SafeHtml} from "@angular/platform-browser";

@Component({
	selector: "app-interchange-style-toggle",
	imports: [
		SelectButtonModule,
		TooltipModule,
		FormsModule,
	],
	templateUrl: "./interchange-style-toggle.component.html",
	styleUrl: "./interchange-style-toggle.component.scss",
})
export class InterchangeStyleToggleComponent implements AfterViewInit {
	private readonly mapDataService = inject(MapDataService);
	private readonly sanitizer = inject(DomSanitizer);

	protected readonly interchangeStyleOptions: { icon: string, value: "DOTTED" | "HOLLOW", tooltip: string }[] = [
		{
			icon: "dots-horizontal",
			value: "DOTTED",
			tooltip: "Dotted",
		},
		{
			icon: "drag-horizontal",
			value: "HOLLOW",
			tooltip: "Hollow",
		},
	];

	private iconCache = new Map<string, SafeHtml>();

	ngAfterViewInit(): void {
		// Trigger Iconify to rebuild with all icons
		setTimeout(() => {
			if ((window as unknown as { Iconify?: { build?: () => void } }).Iconify?.build) {
				(window as unknown as { Iconify: { build: () => void } }).Iconify.build();
			}
		}, 0);
	}

	getInterchangeStyle() {
		return this.mapDataService.interchangeStyle();
	}

	getInterchangeStyleIcon(icon: string): SafeHtml {
		if (!this.iconCache.has(icon)) {
			const iconHtml = `<i class="iconify" data-icon="mdi:${icon}"></i>`;
			this.iconCache.set(icon, this.sanitizer.bypassSecurityTrustHtml(iconHtml));
		}
		return this.iconCache.get(icon)!;
	}

	setInterchangeStyle(event: SelectButtonChangeEvent) {
		this.mapDataService.interchangeStyle.set(event.value);
		this.mapDataService.updateData();
		setCookie("interchange_style", event.value);
	}
}
