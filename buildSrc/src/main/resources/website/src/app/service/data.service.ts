import {plainToInstance} from "class-transformer";
import {arrayAverage, pushIfNotExists, setIfUndefined} from "../data/utilities";
import {ROUTE_TYPES} from "../data/routeType";
import {LineConnection} from "../entity/lineConnection";
import {StationConnection} from "../entity/stationConnection";
import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {ServiceBase} from "./service";
import {DimensionService} from "./dimension.service";
import {RouteStation} from "../entity/generated/routeStation";
import {StationsAndRoutes} from "../entity/generated/stationsAndRoutes";
import {Station} from "../entity/generated/station";
import {Route} from "../entity/generated/route";

const REFRESH_INTERVAL = 30000;

@Injectable({providedIn: "root"})
export class DataService extends ServiceBase<{ currentTime: number, data: StationsAndRoutes }> {
	private routeTypes: { [key: string]: number } = {};
	private tempRoutes: RouteExtended[] = [];
	private stations: StationWithPosition[] = [];
	private allStations: StationWithPosition[] = [];
	private allRoutes: RouteExtended[] = [];
	private stationConnections: StationConnection[] = [];
	private lineConnections: LineConnection[] = [];
	private centerX = 0;
	private centerY = 0;
	private timeOffset = 0;
	private canSetTimeOffset = true;
	public setLoading: () => void = () => {
	};
	public drawMap: () => void = () => {
	};
	public animateCenter: (x: number, z: number) => void = () => {
	};

	constructor(private readonly httpClient: HttpClient, dimensionService: DimensionService) {
		super(() => this.httpClient.get<{ currentTime: number, data: StationsAndRoutes }>(this.getUrl("stations-and-routes")), REFRESH_INTERVAL, dimensionService);
		this.getData("");
	}

	protected override processData(data: { currentTime: number; data: StationsAndRoutes }) {
		if (this.canSetTimeOffset) {
			this.timeOffset = Date.now() - data.currentTime;
			this.canSetTimeOffset = false;
		}

		this.tempRoutes = [];
		const availableRouteTypes: string[] = [];

		data.data.routes.forEach(dataResponseRoute => {
			const stationIds: string [] = [];
			const routeStationsInfo: StationExtended[] = [];
			pushIfNotExists(availableRouteTypes, dataResponseRoute.type);

			dataResponseRoute.stations.forEach(dateResponseRouteStation => {
				const dataResponseStation = data.data.stations.find(dataResponseStation => dataResponseStation.id === dateResponseRouteStation.id);
				if (dataResponseStation !== undefined) {
					pushIfNotExists(stationIds, dateResponseRouteStation.id);
					routeStationsInfo.push(StationExtended.create(dataResponseStation, dateResponseRouteStation));
				}
			});

			if (stationIds.length > 1) {
				this.tempRoutes.push(new RouteExtended(dataResponseRoute, routeStationsInfo));
			}
		});

		const allRouteTypes = Object.keys(ROUTE_TYPES);
		for (let i = 0; i < allRouteTypes.length; i++) {
			const routeType = allRouteTypes[i];
			if (availableRouteTypes.includes(routeType)) {
				setIfUndefined(this.routeTypes, routeType, () => 0);
			} else {
				delete this.routeTypes[routeType];
			}
		}

		if (availableRouteTypes.length > 0 && Object.values(this.routeTypes).every(visibility => visibility === 0)) {
			this.routeTypes[Object.keys(this.routeTypes)[0]] = 1;
		}

		this.dimensionService.setDimensions(data.data.dimensions);
		this.updateData();
	}

	public setDimension(dimension: string) {
		this.setLoading();
		this.dimensionService.setDimension(dimension);
		this.getData("");
	}

	public getRouteTypes() {
		return this.routeTypes;
	}

	public getStations() {
		return this.stations;
	}

	public getAllStations() {
		return this.allStations;
	}

	public getAllRoutes() {
		return this.allRoutes;
	}

	public getStationConnections() {
		return this.stationConnections;
	}

	public getLineConnections() {
		return this.lineConnections;
	}

	public getCenterX() {
		return this.centerX;
	}

	public getCenterY() {
		return this.centerY;
	}

	public getTimeOffset() {
		return this.timeOffset;
	}

	public updateData() {
		const tempStationData: {
			[key: string]: {
				id: string,
				name: string,
				color: string,
				zone1: number,
				zone2: number,
				zone3: number,
				connections: string[],
				xPositions: number[],
				yPositions: number[],
				zPositions: number[],
				xPositionsAll: number[],
				yPositionsAll: number[],
				zPositionsAll: number[],
				types: string[],
			}
		} = {};
		const routeTypes: string[] = [];
		const stationsPhase1: {
			id: string,
			name: string,
			color: string,
			zone1: number,
			zone2: number,
			zone3: number,
			connections: string[],
			x: number,
			y: number,
			z: number,
			rotate: boolean,
			routeCount: number,
			width: number,
			height: number,
			types: string[],
		}[] = [];
		this.allRoutes = [];

		this.tempRoutes.forEach(tempRoute => {
			if (!this.allRoutes.some(route => route.name === tempRoute.name && route.color === tempRoute.color)) {
				this.allRoutes.push(tempRoute);
			}

			const routeTypeSelected = this.routeTypes[tempRoute.type] > 0;
			tempRoute.stations.forEach(({id, name, color, zone1, zone2, zone3, connections, x, y, z}) => {
				setIfUndefined(tempStationData, id, () => ({id, name, color, zone1, zone2, zone3, connections, xPositions: [], yPositions: [], zPositions: [], xPositionsAll: [], yPositionsAll: [], zPositionsAll: [], types: []}));

				if (routeTypeSelected) {
					tempStationData[id].xPositions.push(x);
					tempStationData[id].yPositions.push(y);
					tempStationData[id].zPositions.push(z);
				}

				tempStationData[id].xPositionsAll.push(x);
				tempStationData[id].yPositionsAll.push(y);
				tempStationData[id].zPositionsAll.push(z);
				pushIfNotExists(tempStationData[id].types, tempRoute.type);
				pushIfNotExists(routeTypes, tempRoute.type);
			});
		});

		this.allStations = [];

		Object.values(tempStationData).forEach(({id, name, color, zone1, zone2, zone3, connections, xPositions, yPositions, zPositions, xPositionsAll, yPositionsAll, zPositionsAll, types}) => {
			const tempStation = {
				id,
				name,
				color,
				zone1,
				zone2,
				zone3,
				connections,
				x: arrayAverage(xPositions.length == 0 ? xPositionsAll : xPositions),
				y: arrayAverage(yPositions.length == 0 ? yPositionsAll : yPositions),
				z: arrayAverage(zPositions.length == 0 ? zPositionsAll : zPositions),
				rotate: false,
				routeCount: 0,
				width: 0,
				height: 0,
				types,
			};
			if (xPositions.length > 0 && yPositions.length > 0 && zPositions.length > 0) {
				stationsPhase1.push(tempStation);
			}
			this.allStations.push(plainToInstance(StationWithPosition, tempStation));
		});

		this.updateData2(stationsPhase1);
	}

	private updateData2(stationsPhase1: { id: string, name: string, color: string, zone1: number, zone2: number, zone3: number, connections: string[], x: number, y: number, z: number, rotate: boolean, routeCount: number, width: number, height: number, types: string[] }[]) {
		const lineConnectionsOneWay: { [key: string]: { forwards: boolean, backwards: boolean } } = {};
		const stationRoutes: { [key: string]: { [key: string]: number[] } } = {};
		const stationGroups: { [key: string]: { [key: string]: string[] } } = {};
		const getKeyAndReverseFromStationIds = (stationId1: string, stationId2: string): [string, boolean] => {
			const reverse = stationId1 > stationId2;
			return [`${reverse ? stationId2 : stationId1}_${reverse ? stationId1 : stationId2}`, reverse];
		};

		this.tempRoutes.forEach(({stations, color, type}) => {
			if (this.routeTypes[type] > 0) {
				const iterateStations = (iterateForwards: boolean) => {
					for (let i = 0; i < stations.length; i++) {
						const index = iterateForwards ? i : stations.length - i - 1;
						const currentTempStation = stations[index];
						const currentTempStationId = currentTempStation.id;
						const previousTempStation = stations[index + (iterateForwards ? -1 : 1)];
						const nextTempStation = stations[index + (iterateForwards ? 1 : -1)];
						const tempStation1 = previousTempStation === undefined ? currentTempStation : previousTempStation;
						const tempStation2 = nextTempStation === undefined ? currentTempStation : nextTempStation;
						const colorAndType = `${color}|${type}`;
						setIfUndefined(stationRoutes, currentTempStationId, () => ({}));
						setIfUndefined(stationRoutes[currentTempStationId], colorAndType, () => []);
						stationRoutes[currentTempStationId][colorAndType].push((Math.round(Math.atan2(tempStation2.x - tempStation1.x, tempStation2.z - tempStation1.z) * 4 / Math.PI) + 8) % 4);

						if (nextTempStation !== undefined) {
							const nextStationId = tempStation2.id;
							if (nextStationId !== currentTempStationId) {
								setIfUndefined(stationGroups, currentTempStationId, () => ({}));
								setIfUndefined(stationGroups[currentTempStationId], nextStationId, () => []);
								pushIfNotExists(stationGroups[currentTempStationId][nextStationId], colorAndType);
								if (iterateForwards) {
									const [key, reverse] = getKeyAndReverseFromStationIds(currentTempStationId, nextStationId);
									const newKey = `${key}_${colorAndType}`;
									setIfUndefined(lineConnectionsOneWay, newKey, () => ({forwards: false, backwards: false}));
									lineConnectionsOneWay[newKey][reverse ? "backwards" : "forwards"] = true;
								}
							}
						}
					}
				};

				iterateStations(true);
				iterateStations(false);
			}
		});

		const lineConnections: { [key: string]: { lineConnectionParts: { color: string, oneWay: number, offset1: number, offset2: number }[], direction1: number, direction2: number, x1: number | undefined, x2: number | undefined, z1: number | undefined, z2: number | undefined, length: number } } = {};
		const stationConnections: { [key: string]: { x1: number | undefined, x2: number | undefined, z1: number | undefined, z2: number | undefined, sizeRatio: number, start45: boolean } } = {};
		let closestDistance: number;
		let maxLineConnectionLength = 1;

		stationsPhase1.forEach(station => {
			const combinedGroups: string[][] = [];
			const stationDirection: { [key: string]: number } = {};
			setIfUndefined(stationGroups, station.id, () => ({}));

			Object.values(stationGroups[station.id]).forEach(routeColors1 => {
				const combinedGroup = [...routeColors1];
				Object.values(stationGroups[station.id]).forEach(routeColors2 => {
					if (combinedGroup.some(routeColor => routeColors2.includes(routeColor))) {
						routeColors2.forEach(routeColor => pushIfNotExists(combinedGroup, routeColor));
					}
				});
				const groupToAddTo = combinedGroups.find(existingCombinedGroup => existingCombinedGroup.some(routeColor => combinedGroup.includes(routeColor)));
				if (groupToAddTo === undefined) {
					combinedGroups.push(combinedGroup);
				} else {
					combinedGroup.forEach(routeColor => pushIfNotExists(groupToAddTo, routeColor));
				}
			});

			const testRouteColors: string[] = [];
			combinedGroups.forEach(combinedGroup => combinedGroup.forEach(routeColor => {
				console.assert(!testRouteColors.includes(routeColor), "Duplicate colors in combined groups", combinedGroups);
				testRouteColors.push(routeColor);
			}));

			const routesForDirection: [string[], string[], string[], string[]] = [[], [], [], []];
			combinedGroups.forEach(combinedGroup => {
				const directionsCount = [0, 0, 0, 0];
				combinedGroup.forEach(routeColor => stationRoutes[station.id][routeColor].forEach(direction => directionsCount[direction]++));
				let direction = 0;
				let max = 0;

				for (let i = 0; i < 4; i++) {
					if (directionsCount[i] > max) {
						direction = i;
						max = directionsCount[i];
					}
				}

				combinedGroup.forEach(routeColor => {
					routesForDirection[direction].push(routeColor);
					stationDirection[routeColor] = direction;
				});
			});

			const rotate = routesForDirection[1].length + routesForDirection[3].length > routesForDirection[0].length + routesForDirection[2].length;
			station.rotate = rotate;
			station.routeCount = Object.keys(stationRoutes[station.id]).length;
			station.width = Math.max(Math.max(0, routesForDirection[rotate ? 1 : 0].length - 1), Math.max(0, routesForDirection[rotate ? 0 : 1].length - 1) * Math.SQRT1_2);
			station.height = Math.max(Math.max(0, routesForDirection[rotate ? 3 : 2].length - 1), Math.max(0, routesForDirection[rotate ? 2 : 3].length - 1) * Math.SQRT1_2);

			routesForDirection.forEach(routesForOneDirection => routesForOneDirection.sort());

			Object.entries(stationGroups[station.id]).forEach(groupEntry => {
				const [stationId, routeColors] = groupEntry;
				const [key, reverse] = getKeyAndReverseFromStationIds(station.id, stationId);
				routeColors.sort();
				setIfUndefined(lineConnections, key, () => ({lineConnectionParts: routeColors.map(() => Object.create(null)), direction1: 0, direction2: 0, x1: undefined, x2: undefined, z1: undefined, z2: undefined, length: 0}));
				const index = reverse ? 2 : 1;
				const direction = stationDirection[routeColors[0]];
				const lineConnection = lineConnections[key];
				lineConnection[`direction${index}`] = direction;
				lineConnection[`x${index}`] = station.x;
				lineConnection[`z${index}`] = station.z;

				for (let i = 0; i < routeColors.length; i++) {
					const color = routeColors[i];
					const lineConnectionDetails = lineConnection.lineConnectionParts[i];
					console.assert(lineConnectionDetails.color === undefined || lineConnectionDetails.color === color, "Color and offsets mismatch", lineConnectionDetails);
					lineConnectionDetails.color = color;
					lineConnectionDetails[`offset${index}`] = routesForDirection[direction].indexOf(color) - routesForDirection[direction].length / 2 + 0.5;
					const {forwards, backwards} = lineConnectionsOneWay[`${key}_${color}`];
					lineConnectionDetails.oneWay = forwards && backwards ? 0 : forwards ? 1 : -1;
				}

				if (lineConnection.x1 != undefined && lineConnection.z1 != undefined && lineConnection.x2 != undefined && lineConnection.z2 != undefined) {
					const lineConnectionLength = Math.abs(lineConnection.x2 - lineConnection.x1) + Math.abs(lineConnection.z2 - lineConnection.z1);
					lineConnection.length = lineConnectionLength;
					maxLineConnectionLength = Math.max(maxLineConnectionLength, lineConnectionLength);
				}
			});

			const distance = Math.abs(station.x) + Math.abs(station.z);
			if (closestDistance === undefined || distance < closestDistance) {
				closestDistance = distance;
				this.centerX = -station.x;
				this.centerY = -station.z;
			}

			station.connections.forEach(stationId => {
				const [key, reverse] = getKeyAndReverseFromStationIds(station.id, stationId);
				setIfUndefined(stationConnections, key, () => ({x1: undefined, x2: undefined, z1: undefined, z2: undefined, sizeRatio: 0, start45: false}));
				stationConnections[key][`x${reverse ? 2 : 1}`] = station.x;
				stationConnections[key][`z${reverse ? 2 : 1}`] = station.z;
				const sizeRatio = (Math.max(station.width, station.height) + 1) / (Math.min(station.width, station.height) + 1);
				if (sizeRatio > stationConnections[key].sizeRatio) {
					stationConnections[key].sizeRatio = sizeRatio;
					stationConnections[key].start45 = reverse !== station.rotate;
				}
			});
		});

		stationsPhase1.sort((station1, station2) => {
			if (station1.routeCount === station2.routeCount) {
				return station2.name.length - station1.name.length;
			} else {
				return station2.routeCount - station1.routeCount;
			}
		});

		this.stations = stationsPhase1.map(station => plainToInstance(StationWithPosition, station));
		this.lineConnections = Object.values(lineConnections).sort((lineConnection1, lineConnection2) => lineConnection2.length - lineConnection1.length).map(lineConnection => {
			lineConnection.length = lineConnection.length / (maxLineConnectionLength + 1);
			return plainToInstance(LineConnection, lineConnection);
		});
		this.stationConnections = Object.values(stationConnections).filter(stationConnection => stationConnection.x1 != undefined && stationConnection.x2 != undefined && stationConnection.z1 != undefined && stationConnection.z2 != undefined).map(stationConnection => plainToInstance(StationConnection, stationConnection));
		this.drawMap();
	}
}

export class StationExtended extends RouteStation {
	constructor(
		id: string,
		public readonly name: string,
		public readonly color: string,
		public readonly zone1: number,
		public readonly zone2: number,
		public readonly zone3: number,
		public readonly connections: string[],
		x: number,
		y: number,
		z: number,
	) {
		super(id, x, y, z);
	}

	static create(dataResponseStation: Station, dataResponseRouteStation: RouteStation) {
		return new StationExtended(
			dataResponseStation.id,
			dataResponseStation.name,
			dataResponseStation.color,
			dataResponseStation.zone1,
			dataResponseStation.zone2,
			dataResponseStation.zone3,
			dataResponseStation.connections,
			dataResponseRouteStation.x,
			dataResponseRouteStation.y,
			dataResponseRouteStation.z,
		);
	}
}

export class StationWithPosition extends StationExtended {
	public readonly types: string[] = [];
	public readonly rotate: boolean = false;
	public readonly routes: RouteExtended[] = [];
	public readonly width: number = 0;
	public readonly height: number = 0;
}

export class RouteExtended {
	public readonly name: string;
	public readonly color: string;
	public readonly number: string;
	public readonly type: string;
	public readonly circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE";

	constructor(dataResponseRoute: Route, readonly stations: StationExtended[]) {
		this.name = dataResponseRoute.name;
		this.color = dataResponseRoute.color;
		this.number = dataResponseRoute.number;
		this.type = dataResponseRoute.type;
		this.circularState = dataResponseRoute.circularState;
	}
}
