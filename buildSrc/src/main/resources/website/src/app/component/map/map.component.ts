import {AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild} from "@angular/core";
import {Mouse} from "../../utility/mouse";
import {Callback} from "../../utility/callback";
import SETTINGS from "../../utility/settings";
import {ROUTE_TYPES} from "../../data/routeType";
import {NgForOf, NgIf} from "@angular/common";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {DataService} from "../../service/data.service";
import {MatIcon} from "@angular/material/icon";
import {DrawData, MainData, Message, ResizeData, SetupData} from "./offscreen";
import {StationService} from "../../service/station.service";
import {SplitNamePipe} from "../../pipe/splitNamePipe";

@Component({
	selector: "app-map",
	standalone: true,
	imports: [
		NgForOf,
		MatProgressSpinner,
		NgIf,
		MatIcon,
		SplitNamePipe,
	],
	templateUrl: "./map.component.html",
	styleUrls: ["./map.component.css"],
})
export class MapComponent implements AfterViewInit {
	@Output() onClickStation = new EventEmitter<string>;
	@ViewChild("canvas") private readonly canvas!: ElementRef<HTMLCanvasElement>;
	@ViewChild("wrapper") private readonly wrapper!: ElementRef<HTMLDivElement>;
	loading: boolean = true;
	textLabels: TextLabel[] = [];

	constructor(private readonly dataService: DataService, private readonly stationService: StationService) {
	}

	ngAfterViewInit() {
		const canvasElement = this.canvas.nativeElement;
		const offscreen = canvasElement.transferControlToOffscreen();
		const worker = new Worker(new URL("./offscreen.ts", import.meta.url), {type: "module"});
		worker.postMessage(new Message("setup", new SetupData(offscreen)), [offscreen]);
		worker.onmessage = () => this.loading = false;
		const callback = new Callback<[number, number, number], undefined>();
		const mouse = new Mouse(canvasElement, this.wrapper.nativeElement, (zoom, centerX, centerY) => {
			worker.postMessage(new Message("draw", new DrawData(
				zoom,
				centerX,
				centerY,
				canvasElement.clientWidth * devicePixelRatio,
				canvasElement.clientHeight * devicePixelRatio,
				"",
				"",
				0
			)));
			callback.update([zoom, centerX, centerY]);
		}, (zoom, centerX, centerY) => {
			worker.postMessage(new Message("resize", new ResizeData(
				zoom,
				centerX,
				centerY,
				canvasElement.clientWidth * devicePixelRatio,
				canvasElement.clientHeight * devicePixelRatio,
				"",
				"",
				0,
				devicePixelRatio
			)));
			callback.update([zoom, centerX, centerY]);
		});

		this.dataService.drawMap = () => {
			this.loading = true;
			let renderedTextCount = 0;
			callback.reset();
			callback.add(() => {
				this.textLabels = [];
				renderedTextCount = 0;
			});

			this.dataService.getStations().forEach(station => {
				const {id, name, types, x, z, rotate, width, height} = station;
				const newWidth = width * 3 * SETTINGS.scale;
				const newHeight = height * 3 * SETTINGS.scale;
				const rotatedSize = (newHeight + newWidth) * Math.SQRT1_2;
				const textOffset = (rotate ? rotatedSize : newHeight) + 9 * SETTINGS.scale;
				const icons = types.filter(type => this.dataService.getRouteTypes()[type] === 0).map(type => ROUTE_TYPES[type].icon);

				callback.add(([zoom, centerX, centerY]) => {
					const canvasX = x * zoom + centerX;
					const canvasY = z * zoom + centerY;
					const halfCanvasWidth = canvasElement.clientWidth / 2;
					const halfCanvasHeight = canvasElement.clientHeight / 2;
					if (Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
						this.textLabels.push(new TextLabel(
							id,
							name,
							icons,
							renderedTextCount < SETTINGS.maxText,
							canvasX + halfCanvasWidth,
							canvasY + halfCanvasHeight - textOffset,
							(rotate ? rotatedSize : newWidth) * 2 + 18 * SETTINGS.scale,
							(rotate ? rotatedSize : newHeight) * 2 + 18 * SETTINGS.scale
						));
						renderedTextCount++;
					}
				});
			});

			mouse.setCenterOnFirstDraw(this.dataService.getCenterX(), this.dataService.getCenterY());
			const [zoom, centerX, centerY] = mouse.getCurrentWindowValues();
			const backgroundColorComponents = getComputedStyle(document.body).backgroundColor.match(/\d+/g)!.map(value => parseInt(value));
			worker.postMessage(new Message("main", new MainData(
				zoom,
				centerX,
				centerY,
				canvasElement.clientWidth * devicePixelRatio,
				canvasElement.clientHeight * devicePixelRatio,
				"",
				"",
				this.dataService.getMaxLineConnectionLength(),
				devicePixelRatio,
				this.dataService.getStations(),
				this.dataService.getLineConnections(),
				this.dataService.getStationConnections(),
				(backgroundColorComponents[0] << 16) + (backgroundColorComponents[1] << 8) + backgroundColorComponents[2],
				backgroundColorComponents[0] <= 0x7F,
				this.dataService.getRouteTypes(),
				SETTINGS.interchangeStyle
			)));
			callback.update([zoom, centerX, centerY]);
		};

		this.dataService.animateCenter = (x, z) => mouse.animateCenter(-x, -z);
	}
}

class TextLabel {
	constructor(
		public readonly id: string,
		public readonly text: string,
		public readonly icons: string[],
		public readonly shouldRenderText: boolean,
		public readonly x: number,
		public readonly y: number,
		public readonly stationWidth: number,
		public readonly stationHeight: number
	) {
	}
}
