import * as THREE from "three";
import {AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild} from "@angular/core";
import SETTINGS from "../../utility/settings";
import {NgForOf, NgIf} from "@angular/common";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {DataService} from "../../service/data.service";
import {MatIcon} from "@angular/material/icon";
import {StationService} from "../../service/station.service";
import {connectStations, connectWith45} from "../../utility/drawing";
import {OrbitControls} from "three/examples/jsm/controls/OrbitControls.js";
import {LineMaterial} from "three/examples/jsm/lines/LineMaterial.js";
import {LineGeometry} from "three/examples/jsm/lines/LineGeometry.js";
import {Line2} from "three/examples/jsm/lines/Line2.js";
import Stats from "three/examples/jsm/libs/stats.module.js";
import {rotate, trig45} from "../../data/utilities";
import {ROUTE_TYPES} from "../../data/routeType";
import {SplitNamePipe} from "../../pipe/splitNamePipe";

const blackColor = 0x000000;
const whiteColor = 0xFFFFFF;
const arrowSpacing = 80;
const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
const lineMaterialStationConnectionThin = new LineMaterial({color: 0xFFFFFF, linewidth: 4 * SETTINGS.scale, vertexColors: true});
const lineMaterialStationConnectionThick = new LineMaterial({color: 0xFFFFFF, linewidth: 8 * SETTINGS.scale, vertexColors: true});
const lineMaterialNormal = new LineMaterial({color: 0xFFFFFF, linewidth: 6 * SETTINGS.scale, vertexColors: true});
const lineMaterialThin = new LineMaterial({color: 0xFFFFFF, linewidth: 3 * SETTINGS.scale, vertexColors: true});

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
	@ViewChild("wrapper") private readonly wrapperRef!: ElementRef<HTMLDivElement>;
	@ViewChild("canvas") private readonly canvasRef!: ElementRef<HTMLCanvasElement>;
	@ViewChild("stats") private readonly statsRef!: ElementRef<HTMLDivElement>;
	loading: boolean = true;
	readonly textLabels: TextLabel[] = [];

	private readonly canvas: () => HTMLCanvasElement;
	private readonly scene = new THREE.Scene();
	private readonly camera = new THREE.OrthographicCamera(0, 0, 0, 0, -20, 20);
	private controls: OrbitControls | undefined;
	private stationGeometry: THREE.BufferGeometry | undefined;
	private oneWayArrowGeometry: THREE.BufferGeometry | undefined;
	private lineGeometryNormal: LineGeometry | undefined;
	private lineGeometryThin: LineGeometry | undefined;

	constructor(private readonly dataService: DataService, private readonly stationService: StationService) {
		this.canvas = () => this.canvasRef.nativeElement;
	}

	ngAfterViewInit() {
		const stats = new Stats();
		this.statsRef.nativeElement.append(stats.dom);
		this.scene.background = new THREE.Color(MapComponent.getBackgroundColor()).convertLinearToSRGB();
		const renderer = new THREE.WebGLRenderer({antialias: true, canvas: this.canvas()});
		renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
		let previousZoom = 1;
		let hasUpdate = false;
		let needsCenter = true;

		const draw = () => {
			hasUpdate = true;
			if (this.camera.zoom !== previousZoom) {
				this.createStationBlobs();
				this.createStationConnections();
				this.createLines();
			}
		};

		const animate = () => {
			if (hasUpdate) {
				previousZoom = this.camera.zoom;
				const {clientWidth, clientHeight} = this.canvas();
				if (clientWidth !== renderer.domElement.width || clientHeight !== renderer.domElement.height) {
					renderer.setSize(clientWidth * devicePixelRatio, clientHeight * devicePixelRatio, false);
					this.camera.left = -clientWidth / 2;
					this.camera.right = clientWidth / 2;
					this.camera.top = clientHeight / 2;
					this.camera.bottom = -clientHeight / 2;
					(this.camera as any).aspect = clientWidth / clientHeight;
					lineMaterialStationConnectionThin.resolution.set(clientWidth, clientHeight);
					lineMaterialStationConnectionThick.resolution.set(clientWidth, clientHeight);
					lineMaterialNormal.resolution.set(clientWidth, clientHeight);
					lineMaterialThin.resolution.set(clientWidth, clientHeight);
					this.camera.updateProjectionMatrix();
				}

				renderer.render(this.scene, this.camera);
				this.updateLabels();
				hasUpdate = false;
				this.loading = false;
			}

			stats.update();
			requestAnimationFrame(animate);
		};
		requestAnimationFrame(animate);

		this.controls = new OrbitControls(this.camera, this.wrapperRef.nativeElement);
		this.controls.target.set(0, 0, 0);
		this.controls.update();
		this.controls.mouseButtons = {LEFT: THREE.MOUSE.PAN, MIDDLE: THREE.MOUSE.DOLLY, RIGHT: THREE.MOUSE.PAN};
		this.controls.touches.ONE = THREE.TOUCH.PAN;
		this.controls.zoomToCursor = true;
		this.controls.zoomSpeed = 2;
		this.controls.addEventListener("change", () => draw());
		window.addEventListener("resize", () => draw());

		this.dataService.setLoading = () => this.loading = true;
		this.dataService.drawMap = () => {
			this.loading = true;
			this.scene.background = new THREE.Color(MapComponent.getBackgroundColor()).convertLinearToSRGB();
			this.scene.clear();

			if (needsCenter) {
				this.centerMap();
				needsCenter = false;
			}

			this.stationGeometry = new THREE.BufferGeometry();
			this.createStationBlobs();
			this.scene.add(new THREE.Mesh(this.stationGeometry, materialWithVertexColors));

			this.createStationConnections();
			const interchangeStyle = SETTINGS.interchangeStyle;
			const positions1 = [0, 0, -10000, 0, 0, -10000];
			const positions2 = [0, 0, -10000, 0, 0, -10000];
			this.dataService.getStationConnections().forEach(({x1, z1, x2, z2, start45}) => {
				const write = (offset: number, positions: number[]) => {
					const points: [number, number][] = [];
					connectWith45(points, x1, z1, x2, z2, start45);
					positions.push(points[0][0], -points[0][1], -10000);
					for (let i = 0; i < points.length; i++) {
						positions.push(points[i][0], -points[i][1], -offset);
					}
					positions.push(points[points.length - 1][0], -points[points.length - 1][1], -10000);
				};
				write(interchangeStyle === 0 ? 1 : 0, positions1);
				write(interchangeStyle === 0 ? 2 : 1, positions2);
			});

			const addStationConnection = (lineMaterial: LineMaterial, positions: number[], color: number) => {
				const geometry = new LineGeometry();
				geometry.setPositions(positions);
				const colors: number[] = [];
				MapComponent.setColor(color, colors, positions.length);
				geometry.setColors(colors);
				const line = new Line2(geometry, lineMaterial);
				line.computeLineDistances();
				this.scene.add(line);
			};

			const backgroundColor = MapComponent.getBackgroundColor();
			const darkMode = MapComponent.isDarkMode();
			addStationConnection(lineMaterialStationConnectionThin, positions1, interchangeStyle === 0 === darkMode ? whiteColor : blackColor);
			addStationConnection(lineMaterialStationConnectionThick, positions2, interchangeStyle === 0 ? backgroundColor : darkMode ? whiteColor : blackColor);

			this.lineGeometryNormal = new LineGeometry();
			this.lineGeometryThin = new LineGeometry();
			this.oneWayArrowGeometry = new THREE.BufferGeometry();
			this.createLines();

			const lineNormal = new Line2(this.lineGeometryNormal, lineMaterialNormal);
			lineNormal.computeLineDistances();
			this.scene.add(lineNormal);

			const lineThin = new Line2(this.lineGeometryThin, lineMaterialThin);
			lineThin.computeLineDistances();
			this.scene.add(lineThin);

			this.scene.add(new THREE.Mesh(this.oneWayArrowGeometry, materialWithVertexColors));
			this.updateLabels();
			draw();
		};
	}

	private createStationBlobs() {
		const positions: number[] = [];
		const colors: number[] = [];
		const darkMode = MapComponent.isDarkMode();

		this.dataService.getStations().forEach(({x, z, rotate, width, height}) => {
			const newWidth = width * 3 * SETTINGS.scale / this.camera.zoom;
			const newHeight = height * 3 * SETTINGS.scale / this.camera.zoom;

			const createShape = (radius: number) => {
				const newRadius = radius * SETTINGS.scale / this.camera.zoom;
				const shape = new THREE.Shape();
				const toRadians = (angle: number) => angle * Math.PI / 180;
				shape.moveTo(-newWidth, newHeight + newRadius);
				shape.arc(0, -newRadius, newRadius, toRadians(90), toRadians(180));
				shape.lineTo(-newWidth - newRadius, -newHeight);
				shape.arc(newRadius, 0, newRadius, toRadians(180), toRadians(270));
				shape.lineTo(newWidth, -newHeight - newRadius);
				shape.arc(0, newRadius, newRadius, toRadians(270), toRadians(360));
				shape.lineTo(newWidth + newRadius, newHeight);
				shape.arc(-newRadius, 0, newRadius, toRadians(0), toRadians(90));
				shape.lineTo(-newWidth, newHeight + newRadius);
				return shape;
			};

			const processShape = (radius: number, offset: number, color: number) => {
				const shapePoints = createShape(radius).getPoints(2);
				for (let i = 1; i < shapePoints.length; i++) {
					positions.push(x, -z, offset);
					const point1 = new THREE.Vector2(shapePoints[i - 1].x + x, shapePoints[i - 1].y - z).rotateAround(new THREE.Vector2(x, -z), rotate ? Math.PI / 4 : 0);
					positions.push(point1.x, point1.y, offset);
					const point2 = new THREE.Vector2(shapePoints[i].x + x, shapePoints[i].y - z).rotateAround(new THREE.Vector2(x, -z), rotate ? Math.PI / 4 : 0);
					positions.push(point2.x, point2.y, offset);
					MapComponent.setColor(color, colors, 3);
				}
			};

			processShape(7, -1, darkMode ? whiteColor : blackColor);
			processShape(5, 0, darkMode ? blackColor : whiteColor);
		});

		if (this.stationGeometry) {
			this.stationGeometry.setAttribute("position", new THREE.BufferAttribute(new Float32Array(positions), 3));
			this.stationGeometry.setAttribute("color", new THREE.BufferAttribute(new Uint8Array(colors), 3));
		}
	}

	private createStationConnections() {
		lineMaterialStationConnectionThin.dashed = SETTINGS.interchangeStyle === 0;
		lineMaterialStationConnectionThin.dashSize = 8 * SETTINGS.scale / this.camera.zoom;
		lineMaterialStationConnectionThin.gapSize = 4 * SETTINGS.scale / this.camera.zoom;
	}

	private createLines() {
		const positionsNormal = [0, 0, -10000, 0, 0, -10000];
		const positionsThin = [0, 0, -10000, 0, 0, -10000];
		const positionsArrow: number[] = [];
		const colorsNormal = [0, 0, 0, 0, 0, 0];
		const colorsThin = [0, 0, 0, 0, 0, 0];
		const colorsArrow: number[] = [];
		const backgroundColor = MapComponent.getBackgroundColor();

		const drawArrow = (color: number, angle: number, x: number, y: number, z: number) => {
			const [offset1X, offset1Y] = rotate(3 * SETTINGS.scale / this.camera.zoom, 0, angle);
			const [offset2X, offset2Y] = rotate(0, 3 * SETTINGS.scale / this.camera.zoom, angle);
			positionsArrow.push(x - offset1X, -(y - offset1Y), -z);
			positionsArrow.push(x + offset2X, -(y + offset2Y), -z);
			positionsArrow.push(x + offset1X, -(y + offset1Y), -z);
			positionsArrow.push(x, -y, -z);
			positionsArrow.push(x + offset1X, -(y + offset1Y), -z);
			positionsArrow.push(x + offset1X - offset2X, -(y + offset1Y - offset2Y), -z);
			positionsArrow.push(x - offset1X - offset2X, -(y - offset1Y - offset2Y), -z);
			positionsArrow.push(x - offset1X, -(y - offset1Y), -z);
			positionsArrow.push(x, -y, -z);
			MapComponent.setColor(color, colorsArrow, 9);
		};

		this.dataService.getLineConnections().forEach(({lineConnectionParts, direction1, direction2, x1, z1, x2, z2, length}) => {
			for (let i = 0; i < lineConnectionParts.length; i++) {
				const {color, offset1, offset2, oneWay} = lineConnectionParts[i];
				const colorInt = parseInt(color, 16);
				const colorOffset = (i - lineConnectionParts.length / 2 + 0.5) * 6 * SETTINGS.scale;
				const hollow = this.dataService.getRouteTypes()[color.split("|")[1]] === 2;
				const lineZ = (hollow ? (oneWay === 0 ? -8 : -12) : (oneWay === 0 ? -2 : -5)) - length;

				// z layers
				// 2-3     solid    two-way line
				// 4       solid    one-way arrows
				// 5-6     solid    one-way line
				// 7       hollow   white fill
				// 8-9     hollow   two-way line
				// 10      hollow   one-way arrows
				// 11      hollow   white fill
				// 12-13   hollow   one-way line

				const [points, oneWayPoints] = connectStations(
					x1,
					z1,
					x2,
					z2,
					direction1,
					direction2,
					offset1 * 6 * SETTINGS.scale / this.camera.zoom,
					offset2 * 6 * SETTINGS.scale / this.camera.zoom,
					colorOffset / this.camera.zoom,
					this.canvas().clientWidth,
					this.canvas().clientHeight,
					oneWay,
				);

				if (points.length >= 2) {
					points.forEach(([x, y, offset]) => {
						positionsNormal.push(x, -y, offset ? -10000 : lineZ);
						MapComponent.setColor(colorInt, colorsNormal);
						if (hollow) {
							positionsThin.push(x, -y, offset ? -10000 : lineZ + 1);
							MapComponent.setColor(backgroundColor, colorsThin);
						}
					});
				}

				if (oneWayPoints.length >= 2) {
					oneWayPoints.forEach(([point1X, point1Y, point2X, point2Y, angle]) => {
						const differenceX = point2X - point1X;
						const differenceY = point2Y - point1Y;
						const distance = Math.sqrt(differenceX * differenceX + differenceY * differenceY);
						const scaledArrowSpacing = arrowSpacing * SETTINGS.scale / Math.min(5, this.camera.zoom);
						const arrowCount = Math.floor(distance / scaledArrowSpacing);
						const padding = (distance - arrowCount * scaledArrowSpacing) / 2;
						const [hollowArrowPaddingX, hollowArrowPaddingY] = trig45(-angle + 2, 1.5 * Math.SQRT2 * SETTINGS.scale / this.camera.zoom);

						for (let j = 0; j < arrowCount; j++) {
							const offset = distance == 0 ? 0 : (padding + scaledArrowSpacing * (j + 0.5)) / distance;
							const x = point1X + differenceX * offset;
							const y = point1Y + differenceY * offset;
							if (hollow) {
								drawArrow(colorInt, angle, x - hollowArrowPaddingX, y - hollowArrowPaddingY, hollow ? 10 : 4);
								drawArrow(colorInt, angle, x + hollowArrowPaddingX, y + hollowArrowPaddingY, hollow ? 10 : 4);
							}
							drawArrow(backgroundColor, angle, x, y, hollow ? 10 : 4);
						}
					});
				}
			}
		});

		if (this.lineGeometryNormal) {
			this.lineGeometryNormal.setPositions(positionsNormal);
			this.lineGeometryNormal.setColors(colorsNormal);
		}

		if (this.lineGeometryThin) {
			this.lineGeometryThin.setPositions(positionsThin);
			this.lineGeometryThin.setColors(colorsThin);
		}

		if (this.oneWayArrowGeometry) {
			this.oneWayArrowGeometry.setAttribute("position", new THREE.BufferAttribute(new Float32Array(positionsArrow), 3));
			this.oneWayArrowGeometry.setAttribute("color", new THREE.BufferAttribute(new Float32Array(colorsArrow), 3));
		}
	}

	private updateLabels() {
		this.textLabels.length = 0;
		let renderedTextCount = 0;
		this.dataService.getStations().forEach(({id, name, types, x, z, rotate, width, height}) => {
			const newWidth = width * 3 * SETTINGS.scale;
			const newHeight = height * 3 * SETTINGS.scale;
			const rotatedSize = (newHeight + newWidth) * Math.SQRT1_2;
			const textOffset = (rotate ? rotatedSize : newHeight) + 9 * SETTINGS.scale;
			const icons = types.filter(type => this.dataService.getRouteTypes()[type] === 0).map(type => ROUTE_TYPES[type].icon);
			const canvasX = (x - this.camera.position.x) * this.camera.zoom;
			const canvasY = (z + this.camera.position.y) * this.camera.zoom;
			const halfCanvasWidth = this.canvas().clientWidth / 2;
			const halfCanvasHeight = this.canvas().clientHeight / 2;
			if (Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight) {
				this.textLabels.push(new TextLabel(
					id,
					name,
					icons,
					renderedTextCount < SETTINGS.maxText,
					canvasX + halfCanvasWidth,
					canvasY + halfCanvasHeight - textOffset,
					(rotate ? rotatedSize : newWidth) * 2 + 18 * SETTINGS.scale,
					(rotate ? rotatedSize : newHeight) * 2 + 18 * SETTINGS.scale,
				));
				renderedTextCount++;
			}
		});
	}

	private centerMap() {
		if (this.controls) {
			this.camera.position.x = -this.dataService.getCenterX();
			this.camera.position.y = this.dataService.getCenterY();
			this.controls.target.set(this.camera.position.x, this.camera.position.y, 0);
			this.controls.update();
		}
	}

	private static setColor(color: number, colors: number[], times = 1) {
		const r = (color >> 16) & 0xFF;
		const g = (color >> 8) & 0xFF;
		const b = color & 0xFF;
		for (let i = 0; i < times; i++) {
			colors.push(r / 0xFF, g / 0xFF, b / 0xFF);
		}
	}

	private static getBackgroundColorComponents() {
		return getComputedStyle(document.body).backgroundColor.match(/\d+/g)!.map(value => parseInt(value));
	}

	private static getBackgroundColor() {
		const backgroundColorComponents = this.getBackgroundColorComponents();
		return (backgroundColorComponents[0] << 16) + (backgroundColorComponents[1] << 8) + backgroundColorComponents[2];
	}

	private static isDarkMode() {
		return this.getBackgroundColorComponents()[0] <= 0x7F;
	}
}

class TextLabel {
	public hoverOverride = false;

	constructor(
		public readonly id: string,
		public readonly text: string,
		public readonly icons: string[],
		public readonly shouldRenderText: boolean,
		public readonly x: number,
		public readonly y: number,
		public readonly stationWidth: number,
		public readonly stationHeight: number,
	) {
	}
}
