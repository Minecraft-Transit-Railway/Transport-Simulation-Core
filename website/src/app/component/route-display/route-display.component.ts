import {AfterViewInit, Component, ElementRef, inject, Input, OnChanges, signal, SimpleChanges, ViewChild} from "@angular/core";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {ThemeService} from "../../service/theme.service";
import {TooltipModule} from "primeng/tooltip";
import {DomSanitizer, SafeHtml} from "@angular/platform-browser";

const iconHtmlCache = new Map<string, SafeHtml>();

@Component({
	selector: "app-route-display",
	imports: [
		FormatColorPipe,
		TooltipModule,
	],
	templateUrl: "./route-display.component.html",
	styleUrl: "./route-display.component.scss",
})
export class RouteDisplayComponent implements AfterViewInit, OnChanges {
	private readonly themeService = inject(ThemeService);
	private readonly sanitizer = inject(DomSanitizer);

	@Input() colorAbove?: number;
	@Input() colorBelow?: number;
	@Input({required: true}) isStation = false;
	@Input({required: true}) icons: { icon: string, offset: number, tooltip?: string }[] = [];
	@ViewChild("text") private readonly textRef!: ElementRef<HTMLDivElement>;
	private readonly height = signal(0);

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
		new ResizeObserver(entries => entries.forEach(entry => this.height.set(entry.target.clientHeight))).observe(this.textRef.nativeElement);
		this.scanIconify();
	}

	ngOnChanges(changes: SimpleChanges): void {
		if (changes["icons"]) {
			this.scanIconify();
		}
	}

	getHeight() {
		return this.height();
	}

	isDarkTheme() {
		return this.themeService.isDarkTheme();
	}
}
