import {Component, inject, Input, AfterViewInit} from "@angular/core";
import {MapDataService} from "../../service/map-data.service";
import {setCookie} from "../../data/utilities";
import {TooltipModule} from "primeng/tooltip";
import {SelectButtonChangeEvent, SelectButtonModule} from "primeng/selectbutton";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {DomSanitizer, SafeHtml} from "@angular/platform-browser";

@Component({
	selector: "app-visibility-toggle",
	imports: [
		SelectButtonModule,
		TooltipModule,
		FormsModule,
		ReactiveFormsModule,
	],
	templateUrl: "./visibility-toggle.component.html",
	styleUrl: "./visibility-toggle.component.scss",
})
export class VisibilityToggleComponent implements AfterViewInit {
	private readonly mapDataService = inject(MapDataService);
	private readonly sanitizer = inject(DomSanitizer);

	@Input({required: true}) routeType = "";
	protected readonly visibilityOptions: { icon: string, value: "HIDDEN" | "SOLID" | "HOLLOW" | "DASHED", tooltip: string }[] = [
		{
			icon: "visibility-off",
			value: "HIDDEN",
			tooltip: "Hidden",
		},
		{
			icon: "horizontal-rule",
			value: "SOLID",
			tooltip: "Solid",
		},
		{
			icon: "drag-handle",
			value: "HOLLOW",
			tooltip: "Hollow",
		},
		{
			icon: "more-horiz",
			value: "DASHED",
			tooltip: "Dashed",
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

	getVisibility() {
		return this.mapDataService.routeTypeVisibility()[this.routeType];
	}

	getVisibilityIcon(icon: string): SafeHtml {
		if (!this.iconCache.has(icon)) {
			const iconHtml = `<i class="iconify" data-icon="material-symbols:${icon}"></i>`;
			this.iconCache.set(icon, this.sanitizer.bypassSecurityTrustHtml(iconHtml));
		}
		return this.iconCache.get(icon)!;
	}

	setVisibility(event: SelectButtonChangeEvent) {
		this.mapDataService.routeTypeVisibility()[this.routeType] = event.value;
		this.mapDataService.updateData();
		Object.entries(this.mapDataService.routeTypeVisibility()).forEach(([newRouteTypeKey, visibility]) => setCookie(`visibility_${newRouteTypeKey}`, visibility));
	}
}
