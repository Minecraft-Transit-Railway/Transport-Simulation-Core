import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {Station} from "./data.service";
import {ServiceBase} from "./service";
import {DimensionService} from "./dimension.service";

const REFRESH_INTERVAL = 10000;

@Injectable({providedIn: "root"})
export class DirectionsService extends ServiceBase<{ data: { responseTime: number, directionsSegments: DataResponse[] } }> {
	private originStation?: Station;
	private destinationStation?: Station;
	readonly directions: DirectionsSegment[] = [];

	constructor(private readonly httpClient: HttpClient, dimensionService: DimensionService) {
		super(() => {
			if (this.originStation && this.destinationStation) {
				return this.httpClient.post<{ data: { responseTime: number, directionsSegments: DataResponse[] } }>(this.getUrl("operation/directions"), JSON.stringify({
					startPosition: DirectionsService.stationPositionToObject(this.originStation),
					endPosition: DirectionsService.stationPositionToObject(this.destinationStation),
					maxWalkingDistance: 10000,
				}));
			} else {
				return;
			}
		}, REFRESH_INTERVAL, dimensionService);
	}

	protected override processData(data: { data: { responseTime: number; directionsSegments: DataResponse[] } }) {
		this.directions.length = 0;
		let currentDirectionsSegment: DirectionsSegment;
		let time = Date.now() - data.data.responseTime;
		data.data.directionsSegments.forEach(directionsSegment => {
			time += directionsSegment.waitingTime;
			if (directionsSegment.startPlatformId !== 0) {
				if (!currentDirectionsSegment || directionsSegment.routeId !== currentDirectionsSegment.routeId) {
					currentDirectionsSegment = new DirectionsSegment(directionsSegment, time);
					this.directions.push(currentDirectionsSegment);
					if (directionsSegment.endPlatformId !== 0) {
						currentDirectionsSegment.totalDuration = directionsSegment.duration;
					}
				} else {
					currentDirectionsSegment.intermediateStations.push(directionsSegment.startStationName);
					currentDirectionsSegment.totalDuration += directionsSegment.waitingTime + directionsSegment.duration;
				}
			}
			time += directionsSegment.duration;
		});
	}

	public setOriginStation(originStation: Station) {
		this.originStation = originStation;
		this.directions.length = 0;
		this.getData2();
	}

	public setDestinationStation(destinationStation: Station) {
		this.destinationStation = destinationStation;
		this.directions.length = 0;
		this.getData2();
	}

	clear() {
		this.originStation = undefined;
		this.destinationStation = undefined;
		this.directions.length = 0;
	}

	private getData2() {
		if (this.originStation && this.destinationStation) {
			this.getData(JSON.stringify([DirectionsService.stationPositionToObject(this.originStation), DirectionsService.stationPositionToObject(this.destinationStation)]));
		}
	}

	private static stationPositionToObject(station: Station) {
		return {x: station.x, y: station.y, z: station.z};
	}
}

class DataResponse {
	readonly startPlatformId: number = 0;
	readonly startPlatformName: string = "";
	readonly startStationName: string = "";
	readonly endPlatformId: number = 0;
	readonly routeId: number = 0;
	readonly routeColor: number = 0;
	readonly routeName: string = "";
	readonly routeNumber: string = "";
	readonly routeCircularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE" = "NONE";
	readonly duration: number = 0;
	readonly waitingTime: number = 0;
}

export class DirectionsSegment {
	readonly startPlatformId: number;
	readonly startPlatformName: string;
	readonly startStationName: string;
	readonly routeId: number;
	readonly routeColor: number;
	readonly routeName: string;
	readonly routeNumber: string;
	readonly routeCircularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE" = "NONE";
	totalDuration: number = 0;
	private readonly waitingTime: number;
	readonly time: number;
	readonly intermediateStations: string[] = [];

	constructor(dataResponse: DataResponse, time: number) {
		this.startPlatformId = dataResponse.startPlatformId;
		this.startPlatformName = dataResponse.startPlatformName;
		this.startStationName = dataResponse.startStationName;
		this.routeId = dataResponse.routeId;
		this.routeColor = dataResponse.routeColor;
		this.routeName = dataResponse.routeName;
		this.routeNumber = dataResponse.routeNumber;
		this.routeCircularState = dataResponse.routeCircularState;
		this.waitingTime = dataResponse.waitingTime;
		this.time = time;
	}

	getTotalDuration() {
		return Math.round(this.totalDuration / 1000);
	}

	getTotalWaitingTime() {
		return Math.round(this.waitingTime / 1000);
	}
}
