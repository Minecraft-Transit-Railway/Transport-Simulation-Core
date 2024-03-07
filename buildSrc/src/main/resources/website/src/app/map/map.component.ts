import {AfterViewInit, Component, ElementRef, Input, ViewChild} from "@angular/core";
import {Mouse} from "./mouse";
import {Callback} from "./callback";
import {AppComponent} from "../app.component";
import SETTINGS from "./settings";
import {isCJK} from "../data/utilities";
import {ROUTE_TYPES} from "../data/routeType";
import {NgForOf} from "@angular/common";

@Component({
	selector: "app-map",
	standalone: true,
	imports: [
		NgForOf,
	],
	templateUrl: "./map.component.html",
	styleUrls: ["./map.component.css"],
})
export class MapComponent implements AfterViewInit {

	@Input() appComponent!: AppComponent;
	@ViewChild("canvas") private readonly canvas!: ElementRef<HTMLCanvasElement>;
	@ViewChild("wrapper") private readonly wrapper!: ElementRef<HTMLDivElement>;
	loading: boolean = true;
	textLabels: TextLabel[] = [];
	private readonly callback = new Callback<[number, number, number], undefined>();
	private mouse!: Mouse;
	private canvasElement!: HTMLCanvasElement;
	private worker!: Worker;

	ngAfterViewInit() {
		this.canvasElement = this.canvas.nativeElement;
		const offscreen = this.canvasElement.transferControlToOffscreen();
		this.worker = new Worker(new URL("./offscreen.ts", import.meta.url), {type: "module"});
		this.worker.postMessage({type: "setup", canvas: offscreen}, [offscreen]);
		this.worker.onmessage = () => this.loading = false;
		this.mouse = new Mouse(this.canvasElement, this.wrapper.nativeElement);

		this.mouse.setMouseCallback((zoom, centerX, centerY, blackAndWhite) => {
			this.worker.postMessage({
				type: "draw",
				zoom,
				centerX,
				centerY,
				canvasWidth: this.canvasElement.clientWidth * devicePixelRatio,
				canvasHeight: this.canvasElement.clientHeight * devicePixelRatio,
				blackAndWhite,
			});
			this.callback.update([zoom, centerX, centerY]);
		});

		this.mouse.setResizeCallback((zoom, centerX, centerY, blackAndWhite) => {
			this.worker.postMessage({
				type: "resize",
				zoom,
				centerX,
				centerY,
				canvasWidth: this.canvasElement.clientWidth * devicePixelRatio,
				canvasHeight: this.canvasElement.clientHeight * devicePixelRatio,
				blackAndWhite,
				devicePixelRatio,
			});
			this.callback.update([zoom, centerX, centerY]);
		});
	}

	draw() {
		let renderedTextCount = 0;
		this.callback.reset();
		this.callback.add(() => {
			this.textLabels = [];
			renderedTextCount = 0;
		});

		this.appComponent.getStations().forEach(station => {
			const {name, types, x, z, rotate, width, height} = station;
			const newWidth = width * 3 * SETTINGS.scale;
			const newHeight = height * 3 * SETTINGS.scale;
			const textOffset = (rotate ? Math.max(newHeight, newWidth) * Math.SQRT1_2 : newHeight) + 9 * SETTINGS.scale;
			const textLabelTexts: TextLabelText[] = name.split("|").map(namePart => new TextLabelText(namePart, isCJK(namePart)));
			const icons = types.filter(type => this.appComponent.getRouteTypes()[type] === 0).map(type => ROUTE_TYPES[type].icon).join("");

			this.callback.add(([zoom, centerX, centerY]) => {
				const canvasX = x * zoom + centerX;
				const canvasY = z * zoom + centerY;
				const halfCanvasWidth = this.canvasElement.clientWidth / 2;
				const halfCanvasHeight = this.canvasElement.clientHeight / 2;
				if (renderedTextCount < SETTINGS.maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
					this.textLabels.push(new TextLabel(textLabelTexts, icons, canvasX + halfCanvasWidth, canvasY + halfCanvasHeight + textOffset));
					renderedTextCount++;
				}
			});
		});

		this.mouse.setCenterOnFirstDraw(this.appComponent.getCenterX(), this.appComponent.getCenterY());
		const [zoom, centerX, centerY, blackAndWhite] = this.mouse.getCurrentWindowValues();
		const backgroundColorComponents = getComputedStyle(document.body).backgroundColor.match(/\d+/g)!.map(value => parseInt(value));
		this.worker.postMessage({
			type: "main",
			stations: this.appComponent.getStations(),
			lineConnections: this.appComponent.getLineConnections(),
			maxLineConnectionLength: this.appComponent.getMaxLineConnectionLength(),
			stationConnections: this.appComponent.getStationConnections(),
			zoom,
			centerX,
			centerY,
			blackAndWhite,
			routeTypesSettings: this.appComponent.getRouteTypes(),
			interchangeStyle: SETTINGS.interchangeStyle,
			canvasWidth: this.canvasElement.clientWidth * devicePixelRatio,
			canvasHeight: this.canvasElement.clientHeight * devicePixelRatio,
			devicePixelRatio,
			backgroundColor: (backgroundColorComponents[0] << 16) + (backgroundColorComponents[1] << 8) + backgroundColorComponents[2],
			darkMode: backgroundColorComponents[0] <= 0x7F,
		});
		this.callback.update([zoom, centerX, centerY]);
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
