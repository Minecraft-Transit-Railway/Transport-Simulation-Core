import * as THREE from "three";
import {AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild} from "@angular/core";
import SETTINGS from "../../utility/settings";
import {MapDataService} from "../../service/map-data.service";
import {connectStations, connectWith45} from "../../utility/drawing";
import {OrbitControls} from "three/examples/jsm/controls/OrbitControls.js";
import {LineMaterial} from "three/examples/jsm/lines/LineMaterial.js";
import {LineGeometry} from "three/examples/jsm/lines/LineGeometry.js";
import {Line2} from "three/examples/jsm/lines/Line2.js";
import Stats from "three/examples/jsm/libs/stats.module.js";
import {rotate, trig45} from "../../data/utilities";
import {SplitNamePipe} from "../../pipe/splitNamePipe";
import {ThemeService} from "../../service/theme.service";
import {MapSelectionService} from "../../service/map-selection.service";
import {ProgressSpinnerModule} from "primeng/progressspinner";
import {ClientsService} from "../../service/clients.service";
import {TooltipModule} from "primeng/tooltip";
import {NgOptimizedImage} from "@angular/common";

const blackColor = 0x000000;
const whiteColor = 0xFFFFFF;
const grayColorLight = 0xDDDDDD;
const grayColorDark = 0x222222;
const arrowSpacing = 80;
const clientImageSize = 32;
const clientImagePadding = 5;
const materialWithVertexColors = new THREE.MeshBasicMaterial({vertexColors: true});
const lineMaterialStationConnectionThin = new LineMaterial({color: 0xFFFFFF, linewidth: 4 * SETTINGS.scale, vertexColors: true});
const lineMaterialStationConnectionThick = new LineMaterial({color: 0xFFFFFF, linewidth: 8 * SETTINGS.scale, vertexColors: true});
const lineMaterialNormal = new LineMaterial({color: 0xFFFFFF, linewidth: 6 * SETTINGS.scale, vertexColors: true});
const lineMaterialNormalDashed = new LineMaterial({color: 0xFFFFFF, linewidth: 6 * SETTINGS.scale, vertexColors: true, dashed: true});
const lineMaterialThin = new LineMaterial({color: 0xFFFFFF, linewidth: 3 * SETTINGS.scale, vertexColors: true});
const lineMaterialThinDashed = new LineMaterial({color: 0xFFFFFF, linewidth: 3 * SETTINGS.scale, vertexColors: true, dashed: true});
const animationDuration = 2000;

// Interaction-aware rebuild throttling
const zoomThreshold = 0.08; // 8%
const rebuildDebounceTime = 120;
const simplifyDuringInteraction = true;

@Component({
	selector: "app-map",
	imports: [
		TooltipModule,
		ProgressSpinnerModule,
		SplitNamePipe,
		NgOptimizedImage,
	],
	templateUrl: "./map.component.html",
	styleUrls: ["./map.component.css"],
})
export class MapComponent implements AfterViewInit {
	@Output() stationClicked = new EventEmitter<string>();
	@ViewChild("wrapper") private readonly wrapperRef!: ElementRef<HTMLDivElement>;
	@ViewChild("canvas") private readonly canvasRef!: ElementRef<HTMLCanvasElement>;
	@ViewChild("stats") private readonly statsRef!: ElementRef<HTMLDivElement>;
	loading = true;
	readonly clientGroupsOnRoute: {
		clients: { id: string, name: string }[],
		clientImagePadding: number,
		x: number,
		y: number,
	}[] = [];
	readonly textLabels: TextLabel[] = [];
	readonly clientImageSize = clientImageSize;

	private clientPositions: Record<string, { x: number, y: number }> = {};

	private readonly clientGroupsOnRouteRaw: {
		clients: { id: string, name: string }[],
		x: number,
		y: number,
	}[] = [];

	private readonly canvas: () => HTMLCanvasElement;
	private readonly scene = new THREE.Scene();
	private readonly camera = new THREE.OrthographicCamera(0, 0, 0, 0, -200, 200);
	private controls: OrbitControls | undefined;
	private stationGeometry: THREE.BufferGeometry | undefined;
	private oneWayArrowGeometry: THREE.BufferGeometry | undefined;
	private lineGeometryStationConnectionThin: LineGeometry | undefined;
	private lineGeometryStationConnectionThick: LineGeometry | undefined;
	private lineGeometryNormal: LineGeometry | undefined;
	private lineGeometryNormalDashed: LineGeometry | undefined;
	private lineGeometryThin: LineGeometry | undefined;
	private lineGeometryThinDashed: LineGeometry | undefined;
	private pointsForLineConnection: Record<string, [number, number, boolean][]> = {};

	// Interaction-aware rebuild throttling
	private isInteracting = false;
	private lastBuiltZoom = 1;
	private rebuildTimeoutId: number | undefined;

	// Reusable GPU buffers for stations and arrows
	private stationPositionsBuffer: Float32Array | undefined;
	private stationColorsBuffer: Float32Array | undefined;
	private stationPositionsCount = 0;
	private arrowPositionsBuffer: Float32Array | undefined;
	private arrowColorsBuffer: Float32Array | undefined;
	private arrowPositionsCount = 0;

	constructor(private readonly mapDataService: MapDataService, private readonly mapSelectionService: MapSelectionService, private readonly clientService: ClientsService, private readonly themeService: ThemeService) {
		this.canvas = () => this.canvasRef.nativeElement;
		this.mapDataService.mapLoading.subscribe(() => this.loading = true);
	}

	ngAfterViewInit() {
		const stats = document.location.origin === "http://localhost:4200" ? new Stats() : undefined;
		if (stats) {
			this.statsRef.nativeElement.append(stats.dom);
		}

		this.scene.background = new THREE.Color(this.getBackgroundColor()).convertLinearToSRGB();
		const renderer = new THREE.WebGLRenderer({antialias: true, canvas: this.canvas()});
		renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
		let hasUpdate = false;
		let needsCenter = true;
		let animationStartX = 0;
		let animationStartY = 0;
		let animationTargetX = 0;
		let animationTargetY = 0;
		let animationStartTime = 0;
		let lineNormalDashed: Line2 | undefined;
		let lineThinDashed: Line2 | undefined;

		const updateMaterialsOnly = () => {
			const {clientWidth, clientHeight} = this.canvas();
			lineMaterialStationConnectionThin.resolution.set(clientWidth, clientHeight);
			lineMaterialStationConnectionThick.resolution.set(clientWidth, clientHeight);
			lineMaterialNormal.resolution.set(clientWidth, clientHeight);
			lineMaterialNormalDashed.resolution.set(clientWidth, clientHeight);
			lineMaterialThin.resolution.set(clientWidth, clientHeight);
			lineMaterialThinDashed.resolution.set(clientWidth, clientHeight);

			lineMaterialStationConnectionThin.dashSize = 8 * SETTINGS.scale / this.camera.zoom;
			lineMaterialStationConnectionThin.gapSize = 4 * SETTINGS.scale / this.camera.zoom;

			lineMaterialNormalDashed.dashSize = 8 * SETTINGS.scale / this.camera.zoom;
			lineMaterialNormalDashed.gapSize = 4 * SETTINGS.scale / this.camera.zoom;
			lineMaterialThinDashed.dashSize = 16 * SETTINGS.scale / this.camera.zoom;
			lineMaterialThinDashed.gapSize = 16 * SETTINGS.scale / this.camera.zoom;
		};

		const requestRebuild = (force = false) => {
			if (this.rebuildTimeoutId !== undefined) {
				clearTimeout(this.rebuildTimeoutId);
				this.rebuildTimeoutId = undefined;
			}

			const doRebuild = () => {
				this.createStationBlobs();
				this.createStationConnections();
				this.createLines(() => {
					lineNormalDashed?.computeLineDistances();
					lineThinDashed?.computeLineDistances();
				});
				this.lastBuiltZoom = this.camera.zoom;
				hasUpdate = true;
			};

			if (force || Math.abs(this.camera.zoom - this.lastBuiltZoom) >= zoomThreshold) {
				if (this.isInteracting) {
					this.rebuildTimeoutId = window.setTimeout(() => {
						if (this.isInteracting) {
							requestRebuild(force);
						} else {
							doRebuild();
						}
					}, rebuildDebounceTime);
				} else {
					doRebuild();
				}
			} else {
				updateMaterialsOnly();
				hasUpdate = true;
			}
		};

		const draw = () => {
			hasUpdate = true;
			updateMaterialsOnly();
			requestRebuild(false);
		};

		const animate = () => {
			const animationProgress = Date.now() - animationStartTime;
			if (animationProgress < animationDuration) {
				const animationPercentage = (1 - Math.cos(Math.PI * animationProgress / animationDuration)) / 2;
				this.moveMap(
					animationStartX + (animationTargetX - animationStartX) * animationPercentage,
					animationStartY + (animationTargetY - animationStartY) * animationPercentage,
				);
			}

			if (hasUpdate) {
				const {clientWidth, clientHeight} = this.canvas();
				if (clientWidth !== renderer.domElement.width || clientHeight !== renderer.domElement.height) {
					renderer.setSize(clientWidth * devicePixelRatio, clientHeight * devicePixelRatio, false);
					this.camera.left = -clientWidth / 2;
					this.camera.right = clientWidth / 2;
					this.camera.top = clientHeight / 2;
					this.camera.bottom = -clientHeight / 2;
					(this.camera as unknown as { aspect: number }).aspect = clientWidth / clientHeight;
					this.camera.updateProjectionMatrix();
					updateMaterialsOnly();
				}

				renderer.render(this.scene, this.camera);
				this.updateLabels();
				hasUpdate = false;
				this.loading = false;
			}

			stats?.update();
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

		// Interaction start/end to throttle heavy work
		this.controls.addEventListener("start", () => {
			this.isInteracting = true;
			hasUpdate = true;
		});
		this.controls.addEventListener("end", () => {
			this.isInteracting = false;
			requestRebuild(true);
		});

		this.controls.addEventListener("change", () => draw());
		window.addEventListener("resize", () => requestRebuild(true));

		this.mapDataService.drawMap.subscribe(() => {
			this.loading = true;
			this.scene.background = new THREE.Color(this.getBackgroundColor()).convertLinearToSRGB();
			this.scene.clear();

			if (needsCenter) {
				this.centerMap();
				needsCenter = false;
			}

			this.stationGeometry = new THREE.BufferGeometry();
			this.initOrGrowStationBuffers(1024);
			this.createStationBlobs();
			this.scene.add(new THREE.Mesh(this.stationGeometry, materialWithVertexColors));

			this.lineGeometryStationConnectionThin = new LineGeometry();
			this.lineGeometryStationConnectionThick = new LineGeometry();
			this.lineGeometryNormal = new LineGeometry();
			this.lineGeometryNormalDashed = new LineGeometry();
			this.lineGeometryThin = new LineGeometry();
			this.lineGeometryThinDashed = new LineGeometry();
			this.oneWayArrowGeometry = new THREE.BufferGeometry();
			this.initOrGrowArrowBuffers(1024);
			this.createStationConnections();
			this.createLines(() => {
			});

			const lineStationConnectionThin = new Line2(this.lineGeometryStationConnectionThin, lineMaterialStationConnectionThin);
			lineStationConnectionThin.computeLineDistances();
			this.scene.add(lineStationConnectionThin);

			const lineStationConnectionThick = new Line2(this.lineGeometryStationConnectionThick, lineMaterialStationConnectionThick);
			lineStationConnectionThick.computeLineDistances();
			this.scene.add(lineStationConnectionThick);

			const lineNormal = new Line2(this.lineGeometryNormal, lineMaterialNormal);
			lineNormal.computeLineDistances();
			this.scene.add(lineNormal);

			lineNormalDashed = new Line2(this.lineGeometryNormalDashed, lineMaterialNormalDashed);
			lineNormalDashed.computeLineDistances();
			this.scene.add(lineNormalDashed);

			const lineThin = new Line2(this.lineGeometryThin, lineMaterialThin);
			lineThin.computeLineDistances();
			this.scene.add(lineThin);

			lineThinDashed = new Line2(this.lineGeometryThinDashed, lineMaterialThinDashed);
			lineThinDashed.computeLineDistances();
			this.scene.add(lineThinDashed);

			this.scene.add(new THREE.Mesh(this.oneWayArrowGeometry, materialWithVertexColors));
			this.updateLabels();

			this.lastBuiltZoom = this.camera.zoom;
			draw();
		});

		this.mapSelectionService.updateSelection.subscribe(() => {
			this.lastBuiltZoom = 0;
			requestRebuild(true);
		});

		this.mapDataService.animateMap.subscribe(({x, z}) => {
			animationStartX = this.camera.position.x;
			animationStartY = this.camera.position.y;
			animationTargetX = x;
			animationTargetY = -z;
			animationStartTime = Date.now();
		});

		this.mapDataService.animateClient.subscribe(id => {
			const client = this.clientPositions[id];
			if (client) {
				animationStartX = this.camera.position.x;
				animationStartY = this.camera.position.y;
				animationTargetX = client.x;
				animationTargetY = client.y;
				animationStartTime = Date.now();
			}
		});

		this.clientService.dataProcessed.subscribe(() => {
			this.createStationBlobs();
			requestRebuild(true);
		});
	}

	private initOrGrowStationBuffers(minCapacity: number) {
		if (this.stationGeometry) {
			const grow = (current: Float32Array | undefined, needed: number) => {
				if (!current || current.length < needed) {
					return new Float32Array(Math.max(needed, current ? current.length * 3 : minCapacity));
				}
				return current;
			};

			this.stationPositionsBuffer = grow(this.stationPositionsBuffer, minCapacity);
			this.stationColorsBuffer = grow(this.stationColorsBuffer, minCapacity);
			const positionAttribute = new THREE.BufferAttribute(this.stationPositionsBuffer, 3);
			const colorAttribute = new THREE.BufferAttribute(this.stationColorsBuffer, 3);
			positionAttribute.setUsage(THREE.DynamicDrawUsage);
			colorAttribute.setUsage(THREE.DynamicDrawUsage);
			this.stationGeometry.setAttribute("position", positionAttribute);
			this.stationGeometry.setAttribute("color", colorAttribute);
			this.stationPositionsCount = 0;
		}
	}

	private ensureStationCapacity(additionalFloats: number) {
		if (this.stationGeometry) {
			const growAmount = this.stationPositionsCount + additionalFloats;
			const growIfNeeded = (buffer: Float32Array | undefined) => {
				if (!buffer || buffer.length < growAmount) {
					const next = new Float32Array(Math.max(growAmount, buffer ? buffer.length * 3 : growAmount));
					if (buffer) {
						next.set(buffer.subarray(0, this.stationPositionsCount));
					}
					return next;
				}
				return buffer;
			};

			if (!this.stationPositionsBuffer || this.stationPositionsBuffer.length < growAmount) {
				this.stationPositionsBuffer = growIfNeeded(this.stationPositionsBuffer);
				const positionAttribute = new THREE.BufferAttribute(this.stationPositionsBuffer, 3);
				positionAttribute.setUsage(THREE.DynamicDrawUsage);
				this.stationGeometry.setAttribute("position", positionAttribute);
			}

			if (!this.stationColorsBuffer || this.stationColorsBuffer.length < growAmount) {
				this.stationColorsBuffer = growIfNeeded(this.stationColorsBuffer);
				const colorAttribute = new THREE.BufferAttribute(this.stationColorsBuffer, 3);
				colorAttribute.setUsage(THREE.DynamicDrawUsage);
				this.stationGeometry.setAttribute("color", colorAttribute);
			}
		}
	}

	private commitStationBuffers() {
		if (this.stationGeometry && this.stationPositionsBuffer && this.stationColorsBuffer) {
			const positionAttribute = this.stationGeometry.getAttribute("position") as THREE.BufferAttribute;
			const colorAttribute = this.stationGeometry.getAttribute("color") as THREE.BufferAttribute;
			positionAttribute.addUpdateRange(0, this.stationPositionsCount);
			colorAttribute.addUpdateRange(0, this.stationPositionsCount);
			positionAttribute.needsUpdate = true;
			colorAttribute.needsUpdate = true;

			// drawRange.count expects a vertex count, not float count
			const vertexCount = this.stationPositionsCount / 3;
			this.stationGeometry.setDrawRange(0, vertexCount);

			// Avoid NaN bounding sphere on empty geometry
			if (vertexCount === 0) {
				this.stationGeometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(0, 0, 0), 0);
			}
		}
	}

	private initOrGrowArrowBuffers(minCapacity: number) {
		if (this.oneWayArrowGeometry) {
			const positions = this.arrowPositionsBuffer && this.arrowPositionsBuffer.length >= minCapacity ? this.arrowPositionsBuffer : new Float32Array(minCapacity);
			const colors = this.arrowColorsBuffer && this.arrowColorsBuffer.length >= minCapacity ? this.arrowColorsBuffer : new Float32Array(minCapacity);
			this.arrowPositionsBuffer = positions;
			this.arrowColorsBuffer = colors;
			const positionAttribute = new THREE.BufferAttribute(positions, 3);
			const colorAttribute = new THREE.BufferAttribute(colors, 3);
			positionAttribute.setUsage(THREE.DynamicDrawUsage);
			colorAttribute.setUsage(THREE.DynamicDrawUsage);
			this.oneWayArrowGeometry.setAttribute("position", positionAttribute);
			this.oneWayArrowGeometry.setAttribute("color", colorAttribute);
			this.arrowPositionsCount = 0;
		}
	}

	private ensureArrowCapacity(additionalFloats: number) {
		if (this.oneWayArrowGeometry) {
			const growAmount = this.arrowPositionsCount + additionalFloats;
			const growIfNeeded = (buffer: Float32Array | undefined) => {
				if (!buffer || buffer.length < growAmount) {
					const next = new Float32Array(Math.max(growAmount, buffer ? buffer.length * 3 : growAmount));
					if (buffer) {
						next.set(buffer.subarray(0, this.arrowPositionsCount));
					}
					return next;
				}
				return buffer;
			};

			if (!this.arrowPositionsBuffer || this.arrowPositionsBuffer.length < growAmount) {
				this.arrowPositionsBuffer = growIfNeeded(this.arrowPositionsBuffer);
				const positionAttribute = new THREE.BufferAttribute(this.arrowPositionsBuffer, 3);
				positionAttribute.setUsage(THREE.DynamicDrawUsage);
				this.oneWayArrowGeometry.setAttribute("position", positionAttribute);
			}

			if (!this.arrowColorsBuffer || this.arrowColorsBuffer.length < growAmount) {
				this.arrowColorsBuffer = growIfNeeded(this.arrowColorsBuffer);
				const colorAttribute = new THREE.BufferAttribute(this.arrowColorsBuffer, 3);
				colorAttribute.setUsage(THREE.DynamicDrawUsage);
				this.oneWayArrowGeometry.setAttribute("color", colorAttribute);
			}
		}
	}

	private commitArrowBuffers() {
		if (this.oneWayArrowGeometry) {
			const positionAttribute = this.oneWayArrowGeometry.getAttribute("position") as THREE.BufferAttribute;
			const colorAttribute = this.oneWayArrowGeometry.getAttribute("color") as THREE.BufferAttribute;
			positionAttribute.addUpdateRange(0, this.arrowPositionsCount);
			colorAttribute.addUpdateRange(0, this.arrowPositionsCount);
			positionAttribute.needsUpdate = true;
			colorAttribute.needsUpdate = true;
			this.oneWayArrowGeometry.setDrawRange(0, this.arrowPositionsCount / 3);
		}
	}

	private createStationBlobs() {
		// Reuse GPU buffers; build into typed arrays
		const backgroundColor = this.getBackgroundColor();
		const newClientImagePadding = clientImagePadding * SETTINGS.scale / this.camera.zoom / 2;
		const newClientImageSize = clientImageSize * SETTINGS.scale / this.camera.zoom / 2;
		this.clientPositions = {};
		this.clientGroupsOnRouteRaw.length = 0;
		this.stationPositionsCount = 0;

		const writePointAndColor = (x: number, y: number, z: number, color: number) => {
			const positions = this.stationPositionsBuffer;
			const colors = this.stationColorsBuffer;
			if (positions && colors) {
				// ensure capacity for 3 floats (position) and 3 floats (colour) added together
				this.ensureStationCapacity(6);
				positions[this.stationPositionsCount] = x;
				positions[this.stationPositionsCount + 1] = y;
				positions[this.stationPositionsCount + 2] = z;
				const r = (color >> 16 & 0xFF) / 0xFF;
				const g = (color >> 8 & 0xFF) / 0xFF;
				const b = (color & 0xFF) / 0xFF;
				colors[this.stationPositionsCount] = r;
				colors[this.stationPositionsCount + 1] = g;
				colors[this.stationPositionsCount + 2] = b;
				this.stationPositionsCount += 3;
			}
		};

		const createShape = (radius: number, newWidth: number, newHeight: number) => {
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

		const processShape = (x: number, y: number, radius: number, newWidth: number, newHeight: number, newRotate: boolean, offset: number, color: number) => {
			const shapePoints = createShape(radius, newWidth, newHeight).getPoints(2);
			for (let i = 1; i < shapePoints.length; i++) {
				const point1 = new THREE.Vector2(shapePoints[i - 1].x + x, shapePoints[i - 1].y + y).rotateAround(new THREE.Vector2(x, y), newRotate ? Math.PI / 4 : 0);
				const point2 = new THREE.Vector2(shapePoints[i].x + x, shapePoints[i].y + y).rotateAround(new THREE.Vector2(x, y), newRotate ? Math.PI / 4 : 0);
				writePointAndColor(x, y, offset, color);
				writePointAndColor(point1.x, point1.y, offset, color);
				writePointAndColor(point2.x, point2.y, offset, color);
			}
		};

		this.mapDataService.stationsForMap.forEach(({station, rotate, width, height}) => {
			const {id, x, z} = station;
			const stationSelected = this.mapSelectionService.selectedStations.length === 0 || this.mapSelectionService.selectedStations.includes(id);
			const adjustZ = stationSelected ? 20 : 0;
			const newWidth = width * 3 * SETTINGS.scale / this.camera.zoom;
			const newHeight = height * 3 * SETTINGS.scale / this.camera.zoom;
			processShape(x, -z, 7, newWidth, newHeight, rotate, adjustZ - 1, this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, stationSelected));
			processShape(x, -z, 5, newWidth, newHeight, rotate, adjustZ, this.getColor(whiteColor, blackColor, backgroundColor, backgroundColor, stationSelected));

			const clientGroups = this.clientService.getClientGroupsForStation()[id];
			if (clientGroups) {
				const clientCount = clientGroups.clients.length;
				const newClientImageWidth = newClientImageSize * clientCount + newClientImagePadding * (clientCount - 1);
				processShape(x, -z, 7, newClientImageWidth, newClientImageSize, false, adjustZ - 1, this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, stationSelected));
				processShape(x, -z, 5, newClientImageWidth, newClientImageSize, false, adjustZ, this.getColor(whiteColor, blackColor, backgroundColor, backgroundColor, stationSelected));
			}
		});

		Object.values(this.clientService.allClients).forEach(({id, rawX, rawZ}) => this.clientPositions[id] = {x: rawX, y: -rawZ});
		Object.entries(this.clientService.getClientGroupsForRoute()).forEach(([routeKey, {clients, x, z, route, routeStationId1, routeStationId2}]) => {
			const points = this.pointsForLineConnection[routeKey];
			if (points) {
				let closestX = 0;
				let closestY = 0;
				let shortestDistance = Number.MAX_SAFE_INTEGER;
				for (let i = 1; i < points.length; i++) {
					const [x1, z1] = points[i - 1];
					const [x2, z2] = points[i];
					const {closestPoint, distance} = MapComponent.closestPointAndDistanceToSegment(x1, -z1, x2, -z2, x, -z);
					if (distance < shortestDistance) {
						shortestDistance = distance;
						closestX = closestPoint.x;
						closestY = closestPoint.y;
					}
				}

				const color = route?.color ?? 0;
				const lineSelected = this.mapSelectionService.selectedStations.length === 0 || this.mapSelectionService.selectedStationConnections.some(stationConnection => stationConnection.routeColor === color && stationConnection.stationIds[0] === routeStationId1 && stationConnection.stationIds[1] === routeStationId2);
				const adjustZ = lineSelected ? 18 : -2;
				const clientCount = clients.length;
				const newClientImageWidth = newClientImageSize * clientCount + newClientImagePadding * (clientCount - 1);
				processShape(closestX, closestY, 5, newClientImageWidth, newClientImageSize, false, adjustZ, this.getColor(color, color, grayColorLight, grayColorDark, lineSelected));
				clients.forEach(({id}) => this.clientPositions[id] = {x: closestX, y: closestY});
				this.clientGroupsOnRouteRaw.push({clients, x: closestX, y: closestY});
			}
		});

		this.commitStationBuffers();
	}

	private createStationConnections() {
		lineMaterialStationConnectionThin.dashed = this.mapDataService.interchangeStyle === "DOTTED";
		lineMaterialStationConnectionThin.dashSize = 8 * SETTINGS.scale / this.camera.zoom;
		lineMaterialStationConnectionThin.gapSize = 4 * SETTINGS.scale / this.camera.zoom;

		const positionsThin = [0, 0, -10000, 0, 0, -10000];
		const positionsThick = [0, 0, -10000, 0, 0, -10000];
		const colorsThin = [0, 0, 0, 0, 0, 0];
		const colorsThick = [0, 0, 0, 0, 0, 0];
		const backgroundColor = this.getBackgroundColor();

		this.mapDataService.stationConnections.forEach(({x1, z1, x2, z2, stationId1, stationId2, start45}) => {
			const selected = this.mapSelectionService.selectedStations.length === 0 || this.mapSelectionService.selectedStations.includes(stationId1) && this.mapSelectionService.selectedStations.includes(stationId2);
			const adjustZ = selected ? 20 : 0;

			const write = (offset: number, positions: number[], colors: number[], color: number) => {
				const points: [number, number][] = [];
				connectWith45(points, x1, z1, x2, z2, start45);
				positions.push(points[0][0], -points[0][1], -10000);
				MapComponent.setColor(color, colors);
				points.forEach(point => {
					positions.push(point[0], -point[1], -offset + adjustZ);
					MapComponent.setColor(color, colors);
				});
				positions.push(points[points.length - 1][0], -points[points.length - 1][1], -10000);
				MapComponent.setColor(color, colors);
			};

			write(this.mapDataService.interchangeStyle === "DOTTED" ? 1 : 0, positionsThin, colorsThin, this.mapDataService.interchangeStyle === "DOTTED" ? this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, selected) : this.getColor(whiteColor, blackColor, backgroundColor, backgroundColor, selected));
			write(this.mapDataService.interchangeStyle === "DOTTED" ? 2 : 1, positionsThick, colorsThick, this.mapDataService.interchangeStyle === "DOTTED" ? backgroundColor : this.getColor(blackColor, whiteColor, grayColorLight, grayColorDark, selected));
		});

		if (this.lineGeometryStationConnectionThin) {
			this.lineGeometryStationConnectionThin.setPositions(positionsThin);
			this.lineGeometryStationConnectionThin.setColors(colorsThin);
		}

		if (this.lineGeometryStationConnectionThick) {
			this.lineGeometryStationConnectionThick.setPositions(positionsThick);
			this.lineGeometryStationConnectionThick.setColors(colorsThick);
		}
	}

	private createLines(refreshDashedLines: () => void) {
		// Keep previous pointsForLineConnection during interaction to avoid stutters
		if (!this.isInteracting) {
			this.pointsForLineConnection = {};
		}

		lineMaterialNormalDashed.dashSize = 8 * SETTINGS.scale / this.camera.zoom;
		lineMaterialNormalDashed.gapSize = 4 * SETTINGS.scale / this.camera.zoom;
		lineMaterialThinDashed.dashSize = 16 * SETTINGS.scale / this.camera.zoom;
		lineMaterialThinDashed.gapSize = 16 * SETTINGS.scale / this.camera.zoom;

		const positionsNormal = [0, 0, -10000, 0, 0, -10000];
		const positionsNormalDashed = [0, 0, -10000, 0, 0, -10000];
		const positionsThin = [0, 0, -10000, 0, 0, -10000];
		const positionsThinDashed = [0, 0, -10000, 0, 0, -10000];

		this.arrowPositionsCount = 0;

		const colorsNormal = [0, 0, 0, 0, 0, 0];
		const colorsNormalDashed = [0, 0, 0, 0, 0, 0];
		const colorsThin = [0, 0, 0, 0, 0, 0];
		const colorsThinDashed = [0, 0, 0, 0, 0, 0];
		const backgroundColor = this.getBackgroundColor();

		const putVertex = (arr: Float32Array, offset: number, x: number, y: number, z: number) => {
			arr[offset] = x;
			arr[offset + 1] = y;
			arr[offset + 2] = z;
		};

		const putColor = (arr: Float32Array, offset: number, color: number) => {
			const r = (color >> 16 & 255) / 255;
			const g = (color >> 8 & 255) / 255;
			const b = (color & 255) / 255;
			arr[offset] = r;
			arr[offset + 1] = g;
			arr[offset + 2] = b;
		};

		const drawArrow = (color: number, angle: number, x: number, y: number, z: number) => {
			if (this.oneWayArrowGeometry && this.arrowPositionsBuffer && this.arrowColorsBuffer) {
				const [offset1X, offset1Y] = rotate(3 * SETTINGS.scale / this.camera.zoom, 0, angle);
				const [offset2X, offset2Y] = rotate(0, 3 * SETTINGS.scale / this.camera.zoom, angle);
				const arrowVertices = [
					[x - offset1X, -(y - offset1Y), -z],
					[x + offset2X, -(y + offset2Y), -z],
					[x + offset1X, -(y + offset1Y), -z],
					[x, -y, -z],
					[x + offset1X, -(y + offset1Y), -z],
					[x + offset1X - offset2X, -(y + offset1Y - offset2Y), -z],
					[x - offset1X - offset2X, -(y - offset1Y - offset2Y), -z],
					[x - offset1X, -(y - offset1Y), -z],
					[x, -y, -z],
				];
				const needFloats = arrowVertices.length * 3;
				const needColors = arrowVertices.length * 3;
				this.ensureArrowCapacity(needFloats + needColors);
				arrowVertices.forEach(arrowVertex => {
					if (this.arrowPositionsBuffer && this.arrowColorsBuffer) {
						const base = this.arrowPositionsCount;
						putVertex(this.arrowPositionsBuffer, base, arrowVertex[0], arrowVertex[1], arrowVertex[2]);
						putColor(this.arrowColorsBuffer, base, color);
						this.arrowPositionsCount += 3;
					}
				});
			}
		};

		this.mapDataService.lineConnections.forEach(({lineConnectionParts, direction1, direction2, x1, z1, x2, z2, stationId1, stationId2, length, relativeLength}) => {
			const hidden = length * this.camera.zoom < 10;
			for (let i = 0; i < lineConnectionParts.length; i++) {
				const {color, offset1, offset2, oneWay} = lineConnectionParts[i];
				const colorInt = parseInt(color.split("|")[0]);
				const lineSelected = this.mapSelectionService.selectedStations.length === 0 || this.mapSelectionService.selectedStationConnections.some(stationConnection => stationConnection.routeColor === colorInt && stationConnection.stationIds[0] === stationId1 && stationConnection.stationIds[1] === stationId2);
				const newColorInt = this.getColor(colorInt, colorInt, grayColorLight, grayColorDark, lineSelected);
				const noService = false; // TODO
				const colorOffset = (i - lineConnectionParts.length / 2 + 0.5) * 6 * SETTINGS.scale;
				const routeTypeVisibility = this.mapDataService.routeTypeVisibility[color.split("|")[1]];
				const dashed = routeTypeVisibility === "DASHED";
				const hollow = routeTypeVisibility === "HOLLOW" || dashed;
				const adjustZ = lineSelected ? 20 : 0;
				const lineZ = (hollow ? (oneWay === 0 ? -8 : -12) : (oneWay === 0 ? -2 : -5)) - relativeLength + adjustZ;

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
						const newZ = hidden || offset ? -10000 : lineZ;
						(noService ? positionsNormalDashed : positionsNormal).push(x, -y, newZ);
						MapComponent.setColor(newColorInt, (noService ? colorsNormalDashed : colorsNormal));
						if (hollow) {
							(dashed ? positionsThinDashed : positionsThin).push(x, -y, newZ + 1);
							MapComponent.setColor(backgroundColor, (dashed ? colorsThinDashed : colorsThin));
						}
					});

					// Cache for client grouping; keep existing during interaction to avoid churn
					if (!this.isInteracting) {
						this.pointsForLineConnection[ClientsService.getRouteConnectionKey(stationId1, stationId2, colorInt)] = points;
					}
				}

				const shouldDrawArrows = !(simplifyDuringInteraction && this.isInteracting);
				if (shouldDrawArrows && oneWayPoints.length >= 2) {
					oneWayPoints.forEach(([point1X, point1Y, point2X, point2Y, angle]) => {
						const differenceX = point2X - point1X;
						const differenceY = point2Y - point1Y;
						const distance = Math.sqrt(differenceX * differenceX + differenceY * differenceY);
						const scaledArrowSpacing = arrowSpacing * SETTINGS.scale / Math.min(5, this.camera.zoom);
						const arrowCount = Math.floor(distance / scaledArrowSpacing);
						const padding = (distance - arrowCount * scaledArrowSpacing) / 2;
						const [hollowArrowPaddingX, hollowArrowPaddingY] = trig45(-angle + 2, 1.5 * Math.SQRT2 * SETTINGS.scale / this.camera.zoom);

						for (let j = 0; j < arrowCount; j++) {
							const offset = distance === 0 ? 0 : (padding + scaledArrowSpacing * (j + 0.5)) / distance;
							const x = point1X + differenceX * offset;
							const y = point1Y + differenceY * offset;
							if (hollow) {
								drawArrow(newColorInt, angle, x - hollowArrowPaddingX, y - hollowArrowPaddingY, (hollow ? 10 : 4) - adjustZ);
								drawArrow(newColorInt, angle, x + hollowArrowPaddingX, y + hollowArrowPaddingY, (hollow ? 10 : 4) - adjustZ);
							}
							drawArrow(backgroundColor, angle, x, y, (hollow ? 10 : 4) - adjustZ);
						}
					});
				}
			}
		});

		if (this.lineGeometryNormal) {
			this.lineGeometryNormal.setPositions(positionsNormal);
			this.lineGeometryNormal.setColors(colorsNormal);
		}

		if (this.lineGeometryNormalDashed) {
			this.lineGeometryNormalDashed.setPositions(positionsNormalDashed);
			this.lineGeometryNormalDashed.setColors(colorsNormalDashed);
		}

		if (this.lineGeometryThin) {
			this.lineGeometryThin.setPositions(positionsThin);
			this.lineGeometryThin.setColors(colorsThin);
		}

		if (this.lineGeometryThinDashed) {
			this.lineGeometryThinDashed.setPositions(positionsThinDashed);
			this.lineGeometryThinDashed.setColors(colorsThinDashed);
		}

		this.commitArrowBuffers();
		refreshDashedLines();
	}

	private updateLabels() {
		const halfCanvasWidth = this.canvas().clientWidth / 2;
		const halfCanvasHeight = this.canvas().clientHeight / 2;
		this.textLabels.length = 0;
		let renderedTextCount = 0;

		this.mapDataService.stationsForMap.forEach(({station, rotate, width, height}) => {
			const {id, name, getIcons, x, z} = station;
			const canvasX = (x - this.camera.position.x) * this.camera.zoom;
			const canvasY = (z + this.camera.position.y) * this.camera.zoom;
			const clientGroup = this.clientService.getClientGroupsForStation()[id];

			if (Math.abs(canvasX) <= halfCanvasWidth && Math.abs(canvasY) <= halfCanvasHeight && (clientGroup || renderedTextCount < SETTINGS.maxText * 2) && (this.mapSelectionService.selectedStations.length === 0 || this.mapSelectionService.selectedStations.includes(id))) {
				const newWidth = width * 3 * SETTINGS.scale;
				const newHeight = height * 3 * SETTINGS.scale;
				const clientsHeight = clientGroup ? clientImageSize * SETTINGS.scale / 2 : 0;
				const clientsWidth = clientGroup ? clientsHeight * clientGroup.clients.length + clientImagePadding * SETTINGS.scale * (clientGroup.clients.length - 1) / 2 : 0;
				const rotatedSize = (newHeight + newWidth) * Math.SQRT1_2;
				const textOffset = Math.max(rotate ? rotatedSize : newHeight, clientsHeight) + 9 * SETTINGS.scale;
				const icons = getIcons(type => this.mapDataService.routeTypeVisibility[type] === "HIDDEN");
				this.textLabels.push({
					hoverOverride: false,
					id,
					text: name,
					icons,
					shouldRenderText: !!clientGroup || renderedTextCount < SETTINGS.maxText,
					clients: clientGroup?.clients,
					clientImagePadding: clientImagePadding * SETTINGS.scale,
					x: canvasX + halfCanvasWidth,
					y: canvasY + halfCanvasHeight - textOffset,
					stationWidth: Math.max(rotate ? rotatedSize : newWidth, clientsWidth) * 2 + 18 * SETTINGS.scale,
					stationHeight: Math.max(rotate ? rotatedSize : newHeight, clientsHeight) * 2 + 18 * SETTINGS.scale,
				});
				renderedTextCount++;
			}
		});

		this.clientGroupsOnRoute.length = 0;
		this.clientGroupsOnRouteRaw.forEach(({clients, x, y}) => {
			const canvasX = (x - this.camera.position.x) * this.camera.zoom;
			const canvasY = (-y + this.camera.position.y) * this.camera.zoom;
			this.clientGroupsOnRoute.push({
				clients,
				clientImagePadding: clientImagePadding * SETTINGS.scale,
				x: canvasX + halfCanvasWidth,
				y: canvasY + halfCanvasHeight - clientImageSize * SETTINGS.scale / 2,
			});
		});

		this.clientService.allClientsNotInStationOrRoute.forEach(({id, name, rawX, rawZ}) => {
			const canvasX = (rawX - this.camera.position.x) * this.camera.zoom;
			const canvasY = (rawZ + this.camera.position.y) * this.camera.zoom;
			this.clientGroupsOnRoute.push({
				clients: [{id, name}],
				clientImagePadding: clientImagePadding * SETTINGS.scale,
				x: canvasX + halfCanvasWidth,
				y: canvasY + halfCanvasHeight - clientImageSize * SETTINGS.scale / 2,
			});
		});
	}

	private centerMap() {
		this.moveMap(-this.mapDataService.getCenterX(), this.mapDataService.getCenterY());
	}

	private moveMap(x: number, y: number) {
		if (this.controls) {
			this.camera.position.x = x;
			this.camera.position.y = y;
			this.controls.target.set(this.camera.position.x, this.camera.position.y, 0);
			this.controls.update();
		}
	}

	private getColor(lightColorNormal: number, darkColorNormal: number, lightColorDisabled: number, darkColorDisabled: number, isSelected: boolean) {
		if (isSelected) {
			return this.isDarkTheme() ? darkColorNormal : lightColorNormal;
		} else {
			return this.isDarkTheme() ? darkColorDisabled : lightColorDisabled;
		}
	}

	private getBackgroundColor() {
		const backgroundColorComponents = getComputedStyle(document.body).getPropertyValue("--background-color").match(/#[a-f\d]+/g)?.map(value => parseInt(value.substring(1), 16));
		return backgroundColorComponents ? backgroundColorComponents[0] : 0;
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

	// Thanks ChatGPT
	private static closestPointAndDistanceToSegment(x1: number, y1: number, x2: number, y2: number, pointX: number, pointY: number): { closestPoint: { x: number, y: number }, distance: number } {
		const dx = x2 - x1;
		const dy = y2 - y1;

		// Handle case where segment is a single point
		if (dx === 0 && dy === 0) {
			return {closestPoint: {x: x1, y: y1}, distance: Math.hypot(pointX - x1, pointY - y1)};
		}

		// Compute projection scalar t
		const t = ((pointX - x1) * dx + (pointY - y1) * dy) / (dx * dx + dy * dy);

		// Clamp t to [0, 1] to restrict to the segment
		const tClamped = Math.max(0, Math.min(1, t));

		// Compute closest point on the segment
		const closestX = x1 + tClamped * dx;
		const closestY = y1 + tClamped * dy;

		return {closestPoint: {x: closestX, y: closestY}, distance: Math.hypot(pointX - closestX, pointY - closestY)};
	}
}

class TextLabel {
	public hoverOverride = false;
	public readonly id: string = "";
	public readonly text: string = "";
	public readonly icons: string[] = [];
	public readonly shouldRenderText: boolean = false;
	public readonly clients?: { id: string, name: string }[];
	public readonly clientImagePadding: number = 0;
	public readonly x: number = 0;
	public readonly y: number = 0;
	public readonly stationWidth: number = 0;
	public readonly stationHeight: number = 0;
}
