import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, StationWithPosition} from "./data.service";

const REFRESH_INTERVAL = 3000;
const URL = `${document.location.origin}${document.location.pathname.replace("index.html", "")}mtr/api/operation/arrivals`;
const MAX_ARRIVALS = 5;

@Injectable({providedIn: "root"})
export class StationService {
	public readonly arrivals: Arrival[] = [];
	public readonly routes: { key: string, name: string, number: string, color: number }[] = [];
	private selectedStation?: StationWithPosition;
	private timeoutId = 0;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService) {
		setInterval(() => this.arrivals.forEach(arrival => arrival.calculateValues()), 100);
	}

	private static getData(instance: StationService) {
		if (instance.selectedStation) {
			instance.httpClient.post<{ currentTime: number, data: { arrivals: DataResponse[] } }>(URL, `{"stationIdsHex":["${instance.selectedStation.id}"],"maxCountPerPlatform":${MAX_ARRIVALS}}`).subscribe(({currentTime, data: {arrivals}}) => {
				const timeOffset = new Date().getTime() - currentTime;
				instance.arrivals.length = 0;
				const routes: { [key: string]: { key: string, name: string, number: string, color: number } } = {};
				arrivals.forEach(arrival => {
					const newArrival = new Arrival(arrival, timeOffset);
					instance.arrivals.push(newArrival);
					routes[newArrival.key] = {key: newArrival.key, name: newArrival.routeName, number: newArrival.routeNumber, color: newArrival.routeColor};
				});
				instance.arrivals.sort((arrival1, arrival2) => arrival1.arrival - arrival2.arrival);
				const newRoutes = Object.values(routes);
				newRoutes.sort((route1, route2) => {
					const numberCompare = route1.number.localeCompare(route2.number);
					return numberCompare == 0 ? `${route1.color} ${route1.name}`.localeCompare(`${route2.color} ${route2.name}`) : numberCompare;
				});
				if (JSON.stringify(newRoutes) !== JSON.stringify(instance.routes)) {
					instance.routes.length = 0;
					newRoutes.forEach(route => instance.routes.push(route));
				}
				instance.timeoutId = setTimeout(() => this.getData(instance), REFRESH_INTERVAL);
			});
		}
	}

	public getSelectedStation() {
		return this.selectedStation;
	}

	public setStation(stationId: string) {
		this.selectedStation = this.dataService.getAllStations().find(station => station.id === stationId);
		this.arrivals.length = 0;
		this.routes.length = 0;
		clearTimeout(this.timeoutId);
		StationService.getData(this);
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
	private readonly realtime: boolean;
	readonly departureIndex: number;
	readonly routeName: string;
	readonly routeNumber: string;
	readonly routeColor: number;
	readonly platformName: string;
	readonly cars: string[];
	readonly arrival: number;
	readonly departure: number;
	readonly key: string;
	private arrivalDifference: number = 0;
	private departureDifference: number = 0;

	constructor(dataResponse: DataResponse, timeOffset: number) {
		const terminatingText = dataResponse.isTerminating ? "(Terminating) " : "";
		const circularText = dataResponse.circularState === "CLOCKWISE" ? "Clockwise via " : dataResponse.circularState === "ANTICLOCKWISE" ? "Anticlockwise via " : "";
		this.destination = `${terminatingText}${circularText}${dataResponse.destination}`;
		this.deviation = dataResponse.deviation;
		this.realtime = dataResponse.realtime;
		this.departureIndex = dataResponse.departureIndex;
		this.routeName = dataResponse.routeName;
		this.routeNumber = dataResponse.routeNumber;
		this.routeColor = dataResponse.routeColor;
		this.platformName = dataResponse.platformName;
		this.cars = dataResponse.cars.map(car => car.vehicleId);
		this.arrival = dataResponse.arrival + timeOffset;
		this.departure = dataResponse.departure + timeOffset;
		this.key = `${this.routeName.split("||")[0]} ${this.routeNumber} ${this.routeColor}`;
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
