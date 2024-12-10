import {AfterViewInit, Component, ElementRef, Input, ViewChild} from "@angular/core";
import {MatSelectModule} from "@angular/material/select";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {MatIcon} from "@angular/material/icon";
import {MatTooltipModule} from "@angular/material/tooltip";

@Component({
	selector: "app-route-display",
	standalone: true,
	imports: [
		MatSelectModule,
		FormatColorPipe,
		MatIcon,
		MatTooltipModule,
	],
	templateUrl: "./route-display.component.html",
	styleUrl: "./route-display.component.css",
})
export class RouteDisplayComponent implements AfterViewInit {
	@Input() colorAbove?: number;
	@Input() colorBelow?: number;
	@Input({required: true}) isStation = false;
	@Input({required: true}) icons: { icon: string, offset: number, tooltip?: string }[] = [];
	@ViewChild("text") private readonly textRef!: ElementRef<HTMLDivElement>;
	private height = 0;

	ngAfterViewInit(): void {
		new ResizeObserver(entries => entries.forEach(entry => this.height = entry.target.clientHeight)).observe(this.textRef.nativeElement);
	}

	getHeight() {
		return this.height;
	}
}
