import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, StationWithPosition} from "./data.service";
import {SplitNamePipe} from "../pipe/splitNamePipe";
import {ROUTE_TYPES} from "../data/routeType";
import {ServiceBase} from "./service";
import {DimensionService} from "./dimension.service";

const REFRESH_INTERVAL = 3000;
const MAX_ARRIVALS = 5;

@Injectable({providedIn: "root"})
export class StationService extends ServiceBase<{ data: { arrivals: DataResponse[] } }> {
	public readonly arrivals: Arrival[] = [];
	public readonly routes: { key: string, name: string, number: string, color: number, lineCount: number, typeIcon: string }[] = [];
	private selectedStation?: StationWithPosition;
	private hasTerminating = false;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService, private readonly splitNamePipe: SplitNamePipe, dimensionService: DimensionService) {
		super(() => {
			if (this.selectedStation) {
				return this.httpClient.post<{ data: { arrivals: DataResponse[] } }>(this.getUrl("map/arrivals"), JSON.stringify({
					stationIdsHex: [this.selectedStation.id],
					maxCountPerPlatform: MAX_ARRIVALS,
				}));
			} else {
				return;
			}
		}, REFRESH_INTERVAL, dimensionService);
		setInterval(() => this.arrivals.forEach(arrival => arrival.calculateValues()), 100);
	}

	protected override processData(data: { data: { arrivals: DataResponse[] } }) {
		this.arrivals.length = 0;
		const routes: { [key: string]: { key: string, name: string, number: string, color: number, lineCount: number, typeIcon: string } } = {};
		this.hasTerminating = false;

		data.data.arrivals.forEach(arrival => {
			const newArrival = new Arrival(this.dataService, arrival);
			this.arrivals.push(newArrival);
			routes[newArrival.key] = {
				key: newArrival.key,
				name: newArrival.routeName,
				number: newArrival.routeNumber,
				color: newArrival.routeColor,
				lineCount: Math.max(this.splitNamePipe.transform(newArrival.routeName).length, this.splitNamePipe.transform(newArrival.routeNumber).length),
				typeIcon: newArrival.routeTypeIcon,
			};
			if (newArrival.isTerminating) {
				this.hasTerminating = true;
			}
		});

		this.arrivals.sort((arrival1, arrival2) => arrival1.arrival - arrival2.arrival);

		const newRoutes = Object.values(routes);
		newRoutes.sort((route1, route2) => {
			const linesCompare = route1.lineCount - route2.lineCount;
			if (linesCompare == 0) {
				const numberCompare = route1.number.localeCompare(route2.number);
				return numberCompare == 0 ? `${route1.color} ${route1.name}`.localeCompare(`${route2.color} ${route2.name}`) : numberCompare;
			} else {
				return linesCompare;
			}
		});

		if (JSON.stringify(newRoutes) !== JSON.stringify(this.routes)) {
			this.routes.length = 0;
			newRoutes.forEach(route => this.routes.push(route));
		}
	}

	public getSelectedStation() {
		return this.selectedStation;
	}

	public setStation(stationId: string) {
		this.selectedStation = this.dataService.getAllStations().find(station => station.id === stationId);
		this.arrivals.length = 0;
		this.routes.length = 0;
		this.hasTerminating = false;
		this.getData(stationId);
	}

	public getHasTerminating() {
		return this.hasTerminating;
	}

	public clear() {
		this.selectedStation = undefined;
	}
}

class DataResponse {
	readonly destination: string = "";
	readonly arrival: number = 0;
	readonly departure: number = 0;
	readonly deviation: number = 0;
	readonly realtime: boolean = false;
	readonly departureIndex: number = 0;
	readonly isTerminating: boolean = false;
	readonly routeName: string = "";
	readonly routeNumber: string = "";
	readonly routeColor: number = 0;
	readonly circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE" = "NONE";
	readonly platformName: string = "";
	readonly cars: DataResponseCar[] = [];
}

class DataResponseCar {
	readonly vehicleId: string = "";
}

export class Arrival {
	readonly destination: string;
	private readonly deviation: number;
	readonly realtime: boolean;
	readonly departureIndex: number;
	readonly isTerminating: boolean;
	readonly routeName: string;
	readonly routeNumber: string;
	readonly routeColor: number;
	readonly routeTypeIcon: string;
	readonly circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE";
	readonly platformName: string;
	readonly cars: string[];
	readonly arrival: number;
	readonly departure: number;
	readonly isContinuous: boolean;
	readonly key: string;
	private arrivalDifference: number = 0;
	private departureDifference: number = 0;

	constructor(dataService: DataService, dataResponse: DataResponse) {
		this.destination = dataResponse.destination;
		this.deviation = dataResponse.deviation;
		this.realtime = dataResponse.realtime;
		this.departureIndex = dataResponse.departureIndex;
		this.isTerminating = dataResponse.isTerminating;
		this.routeName = dataResponse.routeName.split("||")[0];
		this.routeNumber = dataResponse.routeNumber;
		this.routeColor = dataResponse.routeColor;
		const tempRouteType = dataService.getAllRoutes().find(route => route.name === dataResponse.routeName)?.type;
		this.routeTypeIcon = tempRouteType == undefined ? "" : ROUTE_TYPES[tempRouteType].icon;
		this.circularState = dataResponse.circularState;
		this.platformName = dataResponse.platformName;
		this.cars = dataResponse.cars.map(car => car.vehicleId);
		this.arrival = dataResponse.arrival === 0 ? 0 : dataResponse.arrival + dataService.getTimeOffset();
		this.departure = dataResponse.departure === 0 ? 0 : dataResponse.departure + dataService.getTimeOffset();
		this.isContinuous = this.arrival === 0;
		this.key = `${this.routeName} ${this.routeNumber} ${this.routeColor}`;
		this.calculateValues();
	}

	public getArrivalTime() {
		return this.arrivalDifference;
	}

	public getDepartureTime() {
		return this.departureDifference;
	}

	public getDeviation() {
		return this.realtime ? Math.abs(Math.round(this.deviation / 1000)) : -1;
	}

	public getDeviationString() {
		return this.realtime ? this.deviation > 0 ? "delay" : "early" : "Scheduled";
	}

	calculateValues() {
		this.arrivalDifference = Math.round((this.arrival - new Date().getTime()) / 1000);
		this.departureDifference = Math.round((this.departure - new Date().getTime()) / 1000);
	}
}
