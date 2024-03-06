import {Component, OnInit} from "@angular/core";
import {MapComponent} from "./map/map.component";
import {arrayAverage, pushIfNotExists, setIfUndefined} from "./data/utilities";
import {plainToInstance, Type} from "class-transformer";
import {ROUTE_TYPES} from "./data/routeType";
import {Station} from "./data/station";
import {LineConnection} from "./data/lineConnection";
import {StationConnection} from "./data/stationConnection";

@Component({
	selector: "app-root",
	standalone: true,
	imports: [MapComponent],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent implements OnInit {

	private readonly url = `${document.location.origin}${document.location.pathname.replace("index.html", "")}mtr/api/map/`;
	private routeTypes: { [key: string]: number } = {};
	private tempRoutes: TempRoute[] = [];
	private stations: Station[] = [];
	private stationConnections: StationConnection[] = [];
	private lineConnections: LineConnection[] = [];
	private maxLineConnectionLength = 0;
	private centerX = 0;
	private centerY = 0;

	ngOnInit() {
		const update = () => this.getData(() => {
			this.updateData1();
			setTimeout(update, 30000);
		});
		update();
	}

	public getRouteTypes() {
		return this.routeTypes;
	}

	public getStations() {
		return this.stations;
	}

	public getStationConnections() {
		return this.stationConnections;
	}

	public getLineConnections() {
		return this.lineConnections;
	}

	public getMaxLineConnectionLength() {
		return this.maxLineConnectionLength;
	}

	public getCenterX() {
		return this.centerX;
	}

	public getCenterY() {
		return this.centerY;
	}

	private getData(callback: () => void) {
		fetch(this.url + "stations-and-routes").then(data => data.json()).then(({data}) => {
			const dataResponse = plainToInstance(DataResponse, data);

			this.tempRoutes = [];
			const availableRouteTypes: string[] = [];

			dataResponse.routes.forEach(dataResponseRoute => {
				const stationIds: string [] = [];
				const routeStationsInfo: TempStation[] = [];
				pushIfNotExists(availableRouteTypes, dataResponseRoute.type);

				dataResponseRoute.stations.forEach(dateResponseRouteStation => {
					const dataResponseStation = dataResponse.stations.find(dataResponseStation => dataResponseStation.id === dateResponseRouteStation.id);
					if (dataResponseStation !== undefined) {
						pushIfNotExists(stationIds, dateResponseRouteStation.id);
						routeStationsInfo.push(new TempStation(dataResponseStation, dateResponseRouteStation));
					}
				});

				if (stationIds.length > 1) {
					this.tempRoutes.push(new TempRoute(dataResponseRoute, routeStationsInfo));
				}
			});

			this.routeTypes = {};
			const allRouteTypes = Object.keys(ROUTE_TYPES);
			for (let i = 0; i < allRouteTypes.length; i++) {
				const routeType = allRouteTypes[i];
				if (availableRouteTypes.includes(routeType)) {
					this.routeTypes[routeType] = i === 0 ? 1 : 0;
				}
			}

			callback();
		});
	}

	private updateData1() {
		const tempStationData: { [key: string]: { id: string, name: string, color: string, zone1: number, zone2: number, zone3: number, connections: string[], xPositions: number[], yPositions: number[], zPositions: number[], types: string[] } } = {};
		const routeTypes: string[] = [];
		const stationsPhase1: { id: string, name: string, color: string, zone1: number, zone2: number, zone3: number, connections: string[], x: number, y: number, z: number, rotate: boolean, routeCount: number, width: number, height: number }[] = [];

		this.tempRoutes.forEach(tempRoute => {
			const routeTypeSelected = this.routeTypes[tempRoute.type] > 0;
			tempRoute.tempStations.forEach(({id, name, color, zone1, zone2, zone3, connections, x, y, z}) => {
				setIfUndefined(tempStationData, id, () => ({id, name, color, zone1, zone2, zone3, connections, xPositions: [], yPositions: [], zPositions: [], types: []}));

				if (routeTypeSelected) {
					tempStationData[id].xPositions.push(x);
					tempStationData[id].yPositions.push(y);
					tempStationData[id].zPositions.push(z);
				}

				pushIfNotExists(tempStationData[id].types, tempRoute.type);
				pushIfNotExists(routeTypes, tempRoute.type);
			});
		});

		Object.values(tempStationData).forEach(({id, name, color, zone1, zone2, zone3, connections, xPositions, yPositions, zPositions}) => {
			if (xPositions.length > 0 && yPositions.length > 0 && zPositions.length > 0) {
				stationsPhase1.push({id, name, color, zone1, zone2, zone3, connections, x: arrayAverage(xPositions), y: arrayAverage(yPositions), z: arrayAverage(zPositions), rotate: false, routeCount: 0, width: 0, height: 0});
			}
		});

		this.updateData2(stationsPhase1);
	}

	private updateData2(stationsPhase1: { id: string, name: string, color: string, zone1: number, zone2: number, zone3: number, connections: string[], x: number, y: number, z: number, rotate: boolean, routeCount: number, width: number, height: number }[]) {
		const lineConnectionsOneWay: { [key: string]: { forwards: boolean, backwards: boolean } } = {};
		const stationRoutes: { [key: string]: { [key: string]: number[] } } = {};
		const stationGroups: { [key: string]: { [key: string]: string[] } } = {};
		const getKeyAndReverseFromStationIds = (stationId1: string, stationId2: string): [string, boolean] => {
			const reverse = stationId1 > stationId2;
			return [`${reverse ? stationId2 : stationId1}_${reverse ? stationId1 : stationId2}`, reverse];
		};

		this.tempRoutes.forEach(({tempStations, color, type}) => {
			if (this.routeTypes[type] > 0) {
				const iterateStations = (iterateForwards: boolean) => {
					for (let i = 0; i < tempStations.length; i++) {
						const index = iterateForwards ? i : tempStations.length - i - 1;
						const currentTempStation = tempStations[index];
						const currentTempStationId = currentTempStation.id;
						const previousTempStation = tempStations[index + (iterateForwards ? -1 : 1)];
						const nextTempStation = tempStations[index + (iterateForwards ? 1 : -1)];
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
		const stationConnections: { [key: string]: { x1: number | undefined, x2: number | undefined, z1: number | undefined, z2: number | undefined } } = {};
		let closestDistance: number;

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
				const [key, reverse] = getKeyAndReverseFromStationIds(station.id, stationId)
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
					this.maxLineConnectionLength = Math.max(this.maxLineConnectionLength, lineConnectionLength);
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
				setIfUndefined(stationConnections, key, () => ({x1: undefined, x2: undefined, z1: undefined, z2: undefined}));
				stationConnections[key][`x${reverse ? 2 : 1}`] = station.x;
				stationConnections[key][`z${reverse ? 2 : 1}`] = station.z;
			});
		});

		stationsPhase1.sort((station1, station2) => {
			if (station1.routeCount === station2.routeCount) {
				return station2.name.length - station1.name.length;
			} else {
				return station2.routeCount - station1.routeCount;
			}
		});

		this.stations = stationsPhase1.map(station => plainToInstance(Station, station));
		this.lineConnections = Object.values(lineConnections).sort((lineConnection1, lineConnection2) => lineConnection2.length - lineConnection1.length).map(lineConnection => plainToInstance(LineConnection, lineConnection));
		this.stationConnections = Object.values(stationConnections).filter(stationConnection => stationConnection.x1 != undefined && stationConnection.x2 != undefined && stationConnection.z1 != undefined && stationConnection.z2 != undefined).map(stationConnection => plainToInstance(StationConnection, stationConnection));
	}
}

class DataResponse {
	@Type(() => DataResponseStation)
	readonly stations: DataResponseStation[] = [];
	@Type(() => DataResponseRoute)
	readonly routes: DataResponseRoute[] = [];
}

class DataResponseStation {
	readonly id: string = "";
	readonly name: string = "";
	readonly color: string = "";
	readonly zone1: number = 0;
	readonly zone2: number = 0;
	readonly zone3: number = 0;
	readonly connections: string[] = [];
}

class DataResponseRoute {
	readonly name: string = "";
	readonly color: string = "";
	readonly number: string = "";
	readonly type: string = "";
	readonly circularState: string = "";
	@Type(() => DataResponseRouteStation)
	readonly stations: DataResponseRouteStation[] = [];
}

class DataResponseRouteStation {
	readonly id: string = "";
	readonly x: number = 0;
	readonly y: number = 0;
	readonly z: number = 0;
}

class TempStation {
	readonly id: string;
	readonly name: string;
	readonly color: string;
	readonly zone1: number;
	readonly zone2: number;
	readonly zone3: number;
	readonly connections: string[];
	readonly x: number;
	readonly y: number;
	readonly z: number;

	constructor(dataResponseStation: DataResponseStation, dataResponseRouteStation: DataResponseRouteStation) {
		this.id = dataResponseStation.id;
		this.name = dataResponseStation.name;
		this.color = dataResponseStation.color;
		this.zone1 = dataResponseStation.zone1;
		this.zone2 = dataResponseStation.zone2;
		this.zone3 = dataResponseStation.zone3;
		this.connections = dataResponseStation.connections;
		this.x = dataResponseRouteStation.x;
		this.y = dataResponseRouteStation.y;
		this.z = dataResponseRouteStation.z;
	}
}

class TempRoute {
	readonly name: string;
	readonly color: string;
	readonly number: string;
	readonly type: string;
	readonly circularState: string;
	readonly tempStations: TempStation[];

	constructor(dataResponseRoute: DataResponseRoute, tempStations: TempStation[]) {
		this.name = dataResponseRoute.name;
		this.color = dataResponseRoute.color;
		this.number = dataResponseRoute.number;
		this.type = dataResponseRoute.type;
		this.circularState = dataResponseRoute.circularState;
		this.tempStations = tempStations;
	}
}
