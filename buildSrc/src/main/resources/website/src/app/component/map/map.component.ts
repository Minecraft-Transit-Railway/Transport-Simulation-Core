import {AfterViewInit, Component, ElementRef, ViewChild} from "@angular/core";
import {Mouse} from "../../utility/mouse";
import {Callback} from "../../utility/callback";
import SETTINGS from "../../utility/settings";
import {isCJK} from "../../data/utilities";
import {ROUTE_TYPES} from "../../data/routeType";
import {NgForOf, NgIf} from "@angular/common";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {DataService} from "../../service/data.service";
import {MatIcon} from "@angular/material/icon";
import {DrawData, MainData, Message, ResizeData, SetupData} from "./offscreen";

@Component({
	selector: "app-map",
	standalone: true,
	imports: [
		NgForOf,
		MatProgressSpinner,
		NgIf,
		MatIcon,
	],
	templateUrl: "./map.component.html",
	styleUrls: ["./map.component.css"],
})
export class MapComponent implements AfterViewInit {

	@ViewChild("canvas") private readonly canvas!: ElementRef<HTMLCanvasElement>;
	@ViewChild("wrapper") private readonly wrapper!: ElementRef<HTMLDivElement>;
	loading: boolean = true;
	textLabels: TextLabel[] = [];

	constructor(private readonly dataService: DataService) {
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
				"0",
				"0",
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
				"0",
				"0",
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
				const {name, types, x, z, rotate, width, height} = station;
				const newWidth = width * 3 * SETTINGS.scale;
				const newHeight = height * 3 * SETTINGS.scale;
				const textOffset = (rotate ? Math.max(newHeight, newWidth) * Math.SQRT1_2 : newHeight) + 9 * SETTINGS.scale;
				const textLabelTexts: TextLabelText[] = name.split("|").map(namePart => new TextLabelText(namePart, isCJK(namePart)));
				const icons = types.filter(type => this.dataService.getRouteTypes()[type] === 0).map(type => ROUTE_TYPES[type].icon);

				callback.add(([zoom, centerX, centerY]) => {
					const canvasX = x * zoom + centerX;
					const canvasY = z * zoom + centerY;
					const halfCanvasWidth = canvasElement.clientWidth / 2;
					const halfCanvasHeight = canvasElement.clientHeight / 2;
					if (renderedTextCount < SETTINGS.maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
						this.textLabels.push(new TextLabel(textLabelTexts, icons, canvasX + halfCanvasWidth, canvasY + halfCanvasHeight + textOffset));
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
				"0",
				"0",
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
	constructor(public readonly text: TextLabelText[], public readonly icons: string[], public readonly x: number, public readonly z: number) {
	}
}

class TextLabelText {
	constructor(public readonly name: string, public readonly isCjk: boolean) {
	}
}
