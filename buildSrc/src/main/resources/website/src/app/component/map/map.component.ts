import * as THREE from "three";
import {AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild} from "@angular/core";
import SETTINGS from "../../utility/settings";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {MapDataService} from "../../service/map-data.service";
import {MatIcon} from "@angular/material/icon";
import {connectStations, connectWith45} from "../../utility/drawing";
import {OrbitControls} from "three/examples/jsm/controls/OrbitControls.js";
import {LineMaterial} from "three/examples/jsm/lines/LineMaterial.js";
import {LineGeometry} from "three/examples/jsm/lines/LineGeometry.js";
import {Line2} from "three/examples/jsm/lines/Line2.js";
import Stats from "three/examples/jsm/libs/stats.module.js";
import {rotate, trig45} from "../../data/utilities";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {RouteKeyService} from "../../service/route.service";
import {ThemeService} from "../../service/theme.service";

const blackColor = 0x000000;
const whiteColor = 0xFFFFFF;
const grayColorLight = 0xDDDDDD;
const grayColorDark = 0x222222;
const arrowSpacing = 80;
const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
const lineMaterialStationConnectionThin = new LineMaterial({color: 0xFFFFFF, linewidth: 4 * SETTINGS.scale, vertexColors: true});
const lineMaterialStationConnectionThick = new LineMaterial({color: 0xFFFFFF, linewidth: 8 * SETTINGS.scale, vertexColors: true});
const lineMaterialNormal = new LineMaterial({color: 0xFFFFFF, linewidth: 6 * SETTINGS.scale, vertexColors: true});
const lineMaterialThin = new LineMaterial({color: 0xFFFFFF, linewidth: 3 * SETTINGS.scale, vertexColors: true});
const animationDuration = 2000;

@Component({
	selector: "app-map",
	imports: [
		MatProgressSpinner,
		MatIcon,
		SplitNamePipe,
	],
	templateUrl: "./map.component.html",
	styleUrls: ["./map.component.css"],
})
export class MapComponent implements AfterViewInit {
	@Output() stationClicked = new EventEmitter<string>();
	@ViewChild("wrapper") private readonly wrapperRef!: ElementRef<HTMLDivElement>;
	@ViewChild("canvas") private readonly canvasRef!: ElementRef<HTMLCanvasElement>;
	@ViewChild("stats") private readonly statsRef!: ElementRef<HTMLDivElement>;
	loading: boolean = true;
	readonly textLabels: TextLabel[] = [];

	private readonly canvas: () => HTMLCanvasElement;
	private readonly scene = new THREE.Scene();
	private readonly camera = new THREE.OrthographicCamera(0, 0, 0, 0, -200, 200);
	private controls: OrbitControls | undefined;
	private stationGeometry: THREE.BufferGeometry | undefined;
	private oneWayArrowGeometry: THREE.BufferGeometry | undefined;
	private lineGeometryNormal: LineGeometry | undefined;
	private lineGeometryThin: LineGeometry | undefined;

	constructor(private readonly dataService: MapDataService, private readonly routeKeyService: RouteKeyService, private readonly themeService: ThemeService) {
		this.canvas = () => this.canvasRef.nativeElement;
		this.dataService.mapLoading.subscribe(() => this.loading = true);
	}

	ngAfterViewInit() {
		this.routeKeyService.selectionChanged.subscribe(() => {
			previousZoom = 0;
			draw();
		});
		const stats = new Stats();
		this.statsRef.nativeElement.append(stats.dom);
		this.scene.background = new THREE.Color(this.getBackgroundColor()).convertLinearToSRGB();
		const renderer = new THREE.WebGLRenderer({antialias: true, canvas: this.canvas()});
		renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
		let previousZoom = 1;
		let hasUpdate = false;
		let needsCenter = true;
		let animationStartX = 0;
		let animationStartY = 0;
		let animationTargetX = 0;
		let animationTargetY = 0;
		let animationStartTime = 0;

		const draw = () => {
			hasUpdate = true;
			if (this.camera.zoom !== previousZoom) {
				this.createStationBlobs();
				this.createStationConnections();
				this.createLines();
			}
		};

		const animate = () => {
			const animationProgress = Date.now() - animationStartTime;
			if (animationProgress < animationDuration) {
				const animationPercentage = (1 - Math.cos(Math.PI * animationProgress / animationDuration)) / 2;
				this.moveMap(animationStartX + (animationTargetX - animationStartX) * animationPercentage, animationStartY + (animationTargetY - animationStartY) * animationPercentage);
			}

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

		this.dataService.drawMap.subscribe(() => {
			this.loading = true;
			this.scene.background = new THREE.Color(this.getBackgroundColor()).convertLinearToSRGB();
			this.scene.clear();

			if (needsCenter) {
				this.centerMap();
				needsCenter = false;
			}

			this.stationGeometry = new THREE.BufferGeometry();
			this.createStationBlobs();
			this.scene.add(new THREE.Mesh(this.stationGeometry, materialWithVertexColors));

			this.createStationConnections();

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
		});

		this.dataService.animateMap.subscribe(({x, z}) => {
			animationStartX = this.camera.position.x;
			animationStartY = this.camera.position.y;
			animationTargetX = x;
			animationTargetY = -z;
			animationStartTime = Date.now();
		});
	}

	private createStationBlobs() {
		const positions: number[] = [];
		const colors: number[] = [];
		const backgroundColor = this.getBackgroundColor();

		this.dataService.stationsForMap.forEach(({station, rotate, width, height}) => {
			const {id, x, z} = station;
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

			const stationSelected = this.routeKeyService.selectedStations.includes(id);
			const adjustZ = this.getSelectedRouteColor() !== undefined && stationSelected;
			processShape(7, -1 + (adjustZ ? 20 : 0), this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, stationSelected));
			processShape(5, adjustZ ? 20 : 0, this.getColor(whiteColor, blackColor, backgroundColor, backgroundColor, stationSelected));
		});

		if (this.stationGeometry) {
			this.stationGeometry.setAttribute("position", new THREE.BufferAttribute(new Float32Array(positions), 3));
			this.stationGeometry.setAttribute("color", new THREE.BufferAttribute(new Float32Array(colors), 3));
		}
	}

	private createStationConnections() {
		lineMaterialStationConnectionThin.dashed = SETTINGS.interchangeStyle === 0;
		lineMaterialStationConnectionThin.dashSize = 8 * SETTINGS.scale / this.camera.zoom;
		lineMaterialStationConnectionThin.gapSize = 4 * SETTINGS.scale / this.camera.zoom;

		const interchangeStyle = SETTINGS.interchangeStyle;
		const positions1 = [0, 0, -10000, 0, 0, -10000];
		const positions2 = [0, 0, -10000, 0, 0, -10000];
		this.dataService.stationConnections.forEach(({x1, z1, x2, z2, start45}) => {
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

		const backgroundColor = this.getBackgroundColor();
		addStationConnection(lineMaterialStationConnectionThin, positions1, interchangeStyle === 0 ? this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, false) : this.getColor(whiteColor, blackColor, backgroundColor, backgroundColor, false));
		addStationConnection(lineMaterialStationConnectionThick, positions2, interchangeStyle === 0 ? backgroundColor : this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, false));
	}

	private createLines() {
		const positionsNormal = [0, 0, -10000, 0, 0, -10000];
		const positionsThin = [0, 0, -10000, 0, 0, -10000];
		const positionsArrow: number[] = [];
		const colorsNormal = [0, 0, 0, 0, 0, 0];
		const colorsThin = [0, 0, 0, 0, 0, 0];
		const colorsArrow: number[] = [];
		const backgroundColor = this.getBackgroundColor();

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

		this.dataService.lineConnections.forEach(({lineConnectionParts, direction1, direction2, x1, z1, x2, z2, stationId1, stationId2, length, relativeLength}) => {
			const lineOffset = length * this.camera.zoom < 10 ? Number.MAX_SAFE_INTEGER : 0;
			for (let i = 0; i < lineConnectionParts.length; i++) {
				const {color, offset1, offset2, oneWay} = lineConnectionParts[i];
				const colorInt = parseInt(color.split("|")[0]);
				const lineSelected = this.getSelectedRouteColor() === colorInt && this.routeKeyService.selectedStationConnections.some(stationConnection => stationConnection[0] === stationId1 && stationConnection[1] === stationId2);
				const newColorInt = this.getColor(colorInt, colorInt, grayColorLight, grayColorDark, lineSelected);
				const colorOffset = (i - lineConnectionParts.length / 2 + 0.5) * 6 * SETTINGS.scale;
				const hollow = this.dataService.routeTypeVisibility[color.split("|")[1]] === "HOLLOW";
				const lineZ = (hollow ? (oneWay === 0 ? -8 : -12) : (oneWay === 0 ? -2 : -5)) - relativeLength + (this.getSelectedRouteColor() !== undefined && lineSelected ? 20 : 0);

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
						positionsNormal.push(x + lineOffset, -y, offset ? -10000 : lineZ);
						MapComponent.setColor(newColorInt, colorsNormal);
						if (hollow) {
							positionsThin.push(x + lineOffset, -y, offset ? -10000 : lineZ + 1);
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
								drawArrow(newColorInt, angle, x - hollowArrowPaddingX, y - hollowArrowPaddingY, hollow ? 10 : 4);
								drawArrow(newColorInt, angle, x + hollowArrowPaddingX, y + hollowArrowPaddingY, hollow ? 10 : 4);
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
		const selectedRouteColor = this.getSelectedRouteColor();
		const stationConnections = this.routeKeyService.selectedStationConnections;
		let renderedTextCount = 0;
		this.dataService.stationsForMap.forEach(({station, rotate, width, height}) => {
			const {id, name, getIcons, x, z} = station;
			const newWidth = width * 3 * SETTINGS.scale;
			const newHeight = height * 3 * SETTINGS.scale;
			const rotatedSize = (newHeight + newWidth) * Math.SQRT1_2;
			const textOffset = (rotate ? rotatedSize : newHeight) + 9 * SETTINGS.scale;
			const icons = getIcons(type => this.dataService.routeTypeVisibility[type] === "HIDDEN");
			const canvasX = (x - this.camera.position.x) * this.camera.zoom;
			const canvasY = (z + this.camera.position.y) * this.camera.zoom;
			const halfCanvasWidth = this.canvas().clientWidth / 2;
			const halfCanvasHeight = this.canvas().clientHeight / 2;
			if (Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight && renderedTextCount < SETTINGS.maxText * 2 && (selectedRouteColor === undefined || stationConnections.some(([stationId1, stationId2]) => id === stationId1 || id === stationId2))) {
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
		this.moveMap(-this.dataService.getCenterX(), this.dataService.getCenterY());
	}

	private moveMap(x: number, y: number) {
		if (this.controls) {
			this.camera.position.x = x;
			this.camera.position.y = y;
			this.controls.target.set(this.camera.position.x, this.camera.position.y, 0);
			this.controls.update();
		}
	}

	private getSelectedRouteColor() {
		const selectedRoutes = this.routeKeyService.getSelectedData();
		return selectedRoutes && selectedRoutes.length > 0 ? selectedRoutes[0].color : undefined;
	}

	private getColor(lightColorNormal: number, darkColorNormal: number, lightColorDisabled: number, darkColorDisabled: number, isSelected: boolean) {
		if (this.getSelectedRouteColor() === undefined || isSelected) {
			return this.isDarkTheme() ? darkColorNormal : lightColorNormal;
		} else {
			return this.isDarkTheme() ? darkColorDisabled : lightColorDisabled;
		}
	}

	private getBackgroundColor() {
		const backgroundColorComponents = getComputedStyle(document.body).getPropertyValue("--mat-sys-surface").match(/#[a-f\d]+/g)?.map(value => parseInt(value.substring(1), 16));
		return backgroundColorComponents ? backgroundColorComponents[this.themeService.isDarkTheme() ? 1 : 0] : 0;
	}

	private isDarkTheme() {
		return this.themeService.isDarkTheme();
	}

	private static setColor(color: number, colors: number[], times = 1) {
		const r = (color >> 16) & 0xFF;
		const g = (color >> 8) & 0xFF;
		const b = color & 0xFF;
		for (let i = 0; i < times; i++) {
			colors.push(r / 0xFF, g / 0xFF, b / 0xFF);
		}
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
