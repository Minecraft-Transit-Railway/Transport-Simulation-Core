import {AfterViewInit, Component, ElementRef, Input, ViewChild} from "@angular/core";
import {Mouse} from "./mouse";
import {Callback} from "./callback";
import {AppComponent} from "../app.component";
import SETTINGS from "./settings";
import {getColorStyle, isCJK} from "../data/utilities";
import {ROUTE_TYPES} from "../data/routeType";

@Component({
	selector: "app-map",
	standalone: true,
	imports: [],
	templateUrl: "./map.component.html",
	styleUrls: ["./map.component.css"],
})
export class MapComponent implements AfterViewInit {

	@Input() appComponent!: AppComponent;
	@ViewChild("canvas") private readonly canvas!: ElementRef;
	loading: boolean = true;
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
		this.mouse = new Mouse(this.canvasElement);

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

		setTimeout(() => this.draw(), 1000);
	}

	draw() {
		let renderedTextCount = 0;
		this.callback.reset();
		this.callback.add(() => renderedTextCount = 0);

		this.appComponent.getStations().forEach(station => {
			const {name, types, x, z, rotate, width, height} = station;
			const newWidth = width * 3 * SETTINGS.scale;
			const newHeight = height * 3 * SETTINGS.scale;
			const textOffset = (rotate ? Math.max(newHeight, newWidth) * Math.SQRT1_2 : newHeight) + 9 * SETTINGS.scale;
			const element = document.createElement("div");
			name.split("|").forEach(namePart => {
				const namePartElement = document.createElement("p");
				namePartElement.innerText = namePart;
				namePartElement.className = `station-name ${isCJK(namePart) ? "cjk" : ""}`;
				element.appendChild(namePartElement);
			});
			const routeTypesIconsElement = document.createElement("p");
			routeTypesIconsElement.className = "station-name material-symbols-outlined";
			routeTypesIconsElement.innerText = types.filter(type => this.appComponent.getRouteTypes()[type] === 0).map(type => ROUTE_TYPES[type].icon).join("");
			element.style.display = "none";
			element.appendChild(routeTypesIconsElement);

			this.callback.add(([zoom, centerX, centerY]) => {
				const canvasX = x * zoom + centerX;
				const canvasY = z * zoom + centerY;
				const halfCanvasWidth = this.canvasElement.clientWidth / 2;
				const halfCanvasHeight = this.canvasElement.clientHeight / 2;
				if (renderedTextCount < SETTINGS.maxText && Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
					element.style.transform = `translate(-50%,0)translate(${canvasX + halfCanvasWidth}px,${canvasY + halfCanvasHeight + textOffset}px)`;
					element.style.display = "";
					renderedTextCount++;
				} else {
					element.style.display = "none";
				}
			});
		});

		const [zoom, centerX, centerY, blackAndWhite] = this.mouse.getCurrentWindowValues();
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
			backgroundColor: getColorStyle("backgroundColor"),
			textColor: getColorStyle("textColor"),
		});
		this.callback.update([zoom, centerX, centerY]);
	}
}
