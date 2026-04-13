import {AfterViewInit, ChangeDetectionStrategy, Component, computed, CUSTOM_ELEMENTS_SCHEMA, ElementRef, inject, input, signal, viewChild} from "@angular/core";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {ThemeService} from "../../service/theme.service";
import {TooltipModule} from "primeng/tooltip";

@Component({
	selector: "app-route-display",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		FormatColorPipe,
		TooltipModule,
	],
	templateUrl: "./route-display.component.html",
	styleUrl: "./route-display.component.scss",
	schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class RouteDisplayComponent implements AfterViewInit {
	private readonly themeService = inject(ThemeService);

	readonly colorAbove = input<number>();
	readonly colorBelow = input<number>();
	readonly isStation = input.required<boolean>();
	readonly icons = input.required<{ icon: string, offset: number, tooltip?: string }[]>();
	private readonly textRef = viewChild.required<ElementRef<HTMLDivElement>>("text");
	private readonly height = signal(0);
	readonly isDarkTheme = computed(() => this.themeService.isDarkTheme());

	ngAfterViewInit(): void {
		new ResizeObserver(entries => entries.forEach(entry => this.height.set(entry.target.clientHeight))).observe(this.textRef().nativeElement);
	}

	getHeight() {
		return this.height();
	}
}
