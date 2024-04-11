import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, StationWithPosition} from "./data.service";
import {setIfUndefined} from "../data/utilities";

const REFRESH_INTERVAL = 3000;
const URL = `${document.location.origin}${document.location.pathname.replace("index.html", "")}mtr/api/operation/arrivals`;
const MAX_ARRIVALS = 5;

@Injectable({providedIn: "root"})
export class StationService {
	private selectedStation?: StationWithPosition;
	private timeoutId = 0;
	private arrivals: { routeName: string, routeColor: string, arrivals: Arrival[] }[] = [];

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService) {
		setInterval(() => this.arrivals.forEach(arrivalGroup => arrivalGroup.arrivals.forEach(arrival => arrival.calculateValues())), 100);
	}

	private static getData(instance: StationService) {
		if (instance.selectedStation) {
			instance.httpClient.post<{ currentTime: number, data: { arrivals: DataResponse[] } }>(URL, `{"stationIdsHex":["${instance.selectedStation.id}"],"maxCountPerPlatform":${MAX_ARRIVALS}}`).subscribe(({currentTime, data: {arrivals}}) => {
				const timeOffset = new Date().getTime() - currentTime;
				const tempArrivals: { [routeColorAndName: string]: { routeName: string, routeColor: string, arrivals: Arrival[] } } = {};
				arrivals.forEach(arrival => {
					const newArrival = new Arrival(arrival, timeOffset);
					const routeColorAndName = `${newArrival.routeColor} ${newArrival.routeName}`;
					setIfUndefined(tempArrivals, routeColorAndName, () => ({routeName: newArrival.routeName, routeColor: newArrival.routeColor, arrivals: []}));
					if (tempArrivals[routeColorAndName].arrivals.length < MAX_ARRIVALS) {
						tempArrivals[routeColorAndName].arrivals.push(newArrival);
					}
				});
				instance.arrivals = Object.entries(tempArrivals)
					.sort(([colorAndRouteName1], [colorAndRouteName2]) => colorAndRouteName1.localeCompare(colorAndRouteName2))
					.map(entry => entry[1]);

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
		clearTimeout(this.timeoutId);
		StationService.getData(this);
	}

	public clear() {
		this.selectedStation = undefined;
	}

	public getArrivals() {
		return this.arrivals;
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
	readonly destination: string = "";
	readonly deviation: number = 0;
	readonly realtime: boolean = false;
	readonly departureIndex: number = 0;
	readonly routeName: string = "";
	readonly routeColor: string = "";
	readonly platformName: string = "";
	readonly cars: string[] = [];
	private readonly arrival: number = 0;
	private readonly departure: number = 0;
	private arrivalDifference: number = 0;
	private departureDifference: number = 0;

	constructor(dataResponse: DataResponse, timeOffset: number) {
		const terminatingText = dataResponse.isTerminating ? "(Terminating) " : "";
		const routeNumberText = dataResponse.routeNumber === "" ? "" : `${dataResponse.routeNumber} `;
		const circularText = dataResponse.circularState === "CLOCKWISE" ? "Clockwise via " : dataResponse.circularState === "ANTICLOCKWISE" ? "Anticlockwise via " : "";
		this.destination = `${terminatingText}${routeNumberText}${circularText}${dataResponse.destination}`;
		this.deviation = dataResponse.deviation;
		this.realtime = dataResponse.realtime;
		this.departureIndex = dataResponse.departureIndex;
		this.routeName = dataResponse.routeName.split("||")[0];
		this.routeColor = dataResponse.routeColor.toString(16).padStart(6, "0");
		this.platformName = dataResponse.platformName;
		this.cars = dataResponse.cars.map(car => car.vehicleId);
		this.arrival = dataResponse.arrival + timeOffset;
		this.departure = dataResponse.departure + timeOffset;
		this.calculateValues();
	}

	public getArrivalTime() {
		return this.arrivalDifference;
	}

	public getDepartureTime() {
		return this.departureDifference;
	}

	calculateValues() {
		this.arrivalDifference = Math.round((this.arrival - new Date().getTime()) / 1000);
		this.departureDifference = Math.round((this.departure - new Date().getTime()) / 1000);
	}
}
