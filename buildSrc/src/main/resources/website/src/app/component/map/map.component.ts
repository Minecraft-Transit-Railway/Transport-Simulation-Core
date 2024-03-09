import {AfterViewInit, Component, ElementRef, ViewChild} from "@angular/core";
import {Mouse} from "../../utility/mouse";
import {Callback} from "../../utility/callback";
import SETTINGS from "../../utility/settings";
import {isCJK} from "../../data/utilities";
import {ROUTE_TYPES} from "../../data/routeType";
import {NgForOf, NgIf} from "@angular/common";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {DataService} from "../../service/data.service";

@Component({
	selector: "app-map",
	standalone: true,
	imports: [
		NgForOf,
		MatProgressSpinner,
		NgIf,
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
		worker.postMessage({type: "setup", canvas: offscreen}, [offscreen]);
		worker.onmessage = () => this.loading = false;
		const callback = new Callback<[number, number, number], undefined>();
		const mouse = new Mouse(canvasElement, this.wrapper.nativeElement, (zoom, centerX, centerY, blackAndWhite) => {
			worker.postMessage({
				type: "draw",
				zoom,
				centerX,
				centerY,
				canvasWidth: canvasElement.clientWidth * devicePixelRatio,
				canvasHeight: canvasElement.clientHeight * devicePixelRatio,
				blackAndWhite,
			});
			callback.update([zoom, centerX, centerY]);
		}, (zoom, centerX, centerY, blackAndWhite) => {
			worker.postMessage({
				type: "resize",
				zoom,
				centerX,
				centerY,
				canvasWidth: canvasElement.clientWidth * devicePixelRatio,
				canvasHeight: canvasElement.clientHeight * devicePixelRatio,
				blackAndWhite,
				devicePixelRatio,
			});
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
				const icons = types.filter(type => this.dataService.getRouteTypes()[type] === 0).map(type => ROUTE_TYPES[type].icon).join("");

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
			const [zoom, centerX, centerY, blackAndWhite] = mouse.getCurrentWindowValues();
			const backgroundColorComponents = getComputedStyle(document.body).backgroundColor.match(/\d+/g)!.map(value => parseInt(value));
			worker.postMessage({
				type: "main",
				stations: this.dataService.getStations(),
				lineConnections: this.dataService.getLineConnections(),
				maxLineConnectionLength: this.dataService.getMaxLineConnectionLength(),
				stationConnections: this.dataService.getStationConnections(),
				zoom,
				centerX,
				centerY,
				blackAndWhite,
				routeTypesSettings: this.dataService.getRouteTypes(),
				interchangeStyle: SETTINGS.interchangeStyle,
				canvasWidth: canvasElement.clientWidth * devicePixelRatio,
				canvasHeight: canvasElement.clientHeight * devicePixelRatio,
				devicePixelRatio,
				backgroundColor: (backgroundColorComponents[0] << 16) + (backgroundColorComponents[1] << 8) + backgroundColorComponents[2],
				darkMode: backgroundColorComponents[0] <= 0x7F,
			});
			callback.update([zoom, centerX, centerY]);
		};
	}
}

class TextLabel {
	constructor(public readonly text: TextLabelText[], public readonly icons: string, public readonly x: number, public readonly z: number) {
	}
}

class TextLabelText {
	constructor(public readonly name: string, public readonly isCjk: boolean) {
	}
}
