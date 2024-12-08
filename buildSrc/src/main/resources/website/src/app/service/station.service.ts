import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, StationWithPosition} from "./data.service";
import {SplitNamePipe} from "../pipe/splitNamePipe";
import {ROUTE_TYPES} from "../data/routeType";
import {ServiceBase} from "./service";
import {DimensionService} from "./dimension.service";
import {SimplifyRoutesPipe} from "../pipe/simplifyRoutesPipe";

const REFRESH_INTERVAL = 3000;
const MAX_ARRIVALS = 5;

@Injectable({providedIn: "root"})
export class StationService extends ServiceBase<{ data: { arrivals: DataResponse[] } }> {
	public readonly arrivalsRoutes: { key: string, name: string, number: string, colorInt: number, textLineCount: number, typeIcon: string }[] = [];
	public readonly routesAtStation: { name: string, variations: string[], number: string, color: string, colorInt: number, typeIcon: string }[] = [];
	private selectedStation?: StationWithPosition;
	private readonly arrivals: Arrival[] = [];
	private hasTerminating = false;
	private readonly filterArrivalRoutes: string[] = [];
	private filterArrivalShowTerminating = false;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService, private readonly splitNamePipe: SplitNamePipe, dimensionService: DimensionService) {
		super(() => {
			if (this.selectedStation) {
				return this.httpClient.post<{ data: { arrivals: DataResponse[] } }>(this.getUrl("arrivals"), JSON.stringify({
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
		const routes: { [key: string]: { key: string, name: string, number: string, color: string, colorInt: number, textLineCount: number, typeIcon: string } } = {};
		this.hasTerminating = false;

		data.data.arrivals.forEach(arrival => {
			const newArrival = new Arrival(this.dataService, arrival);
			this.arrivals.push(newArrival);
			routes[newArrival.key] = {
				key: newArrival.key,
				name: newArrival.routeName,
				number: newArrival.routeNumber,
				color: newArrival.routeColor,
				colorInt: newArrival.routeColorInt,
				textLineCount: Math.max(2, Math.max(this.splitNamePipe.transform(newArrival.routeName).length, this.splitNamePipe.transform(newArrival.routeNumber).length)),
				typeIcon: newArrival.routeTypeIcon,
			};
			if (newArrival.isTerminating) {
				this.hasTerminating = true;
			}
		});

		this.arrivals.sort((arrival1, arrival2) => arrival1.arrival - arrival2.arrival);
		const newRoutes = Object.values(routes);
		SimplifyRoutesPipe.sortRoutes(newRoutes);

		if (JSON.stringify(newRoutes) !== JSON.stringify(this.arrivalsRoutes)) {
			this.arrivalsRoutes.length = 0;
			newRoutes.forEach(route => this.arrivalsRoutes.push(route));
		}
	}

	public getSelectedStation() {
		return this.selectedStation;
	}

	public setStation(stationId: string, zoomToStation: boolean) {
		this.selectedStation = this.dataService.getAllStations().find(station => station.id === stationId);
		this.arrivals.length = 0;
		this.arrivalsRoutes.length = 0;

		this.routesAtStation.length = 0;
		const newRoutes: { [key: string]: { name: string, variations: string[], number: string, color: string, colorInt: number, typeIcon: string } } = {};
		this.dataService.getAllRoutes().forEach(({name, number, color, type, stations}) => {
			if (stations.some(station => station.id === this.selectedStation?.id)) {
				const key = SimplifyRoutesPipe.getRouteId({name, number, color});
				const variation = name.split("||")[1];
				if (key in newRoutes) {
					newRoutes[key].variations.push(variation);
				} else {
					newRoutes[key] = {name: name.split("||")[0], variations: [variation], number, color, colorInt: parseInt(color, 16), typeIcon: ROUTE_TYPES[type].icon};
				}
			}
		});
		Object.values(newRoutes).forEach(route => {
			route.variations.sort();
			this.routesAtStation.push(route);
		});
		SimplifyRoutesPipe.sortRoutes(this.routesAtStation);

		this.hasTerminating = false;
		this.getData(stationId);
		this.resetArrivalFilter();

		if (this.selectedStation) {
			if (this.selectedStation.types.every(routeType => this.dataService.getRouteTypes()[routeType] === 0)) {
				this.selectedStation.types.forEach(routeType => this.dataService.getRouteTypes()[routeType] = 1);
				this.dataService.updateData();
			}
			if (zoomToStation) {
				this.dataService.animateCenter(this.selectedStation.x, this.selectedStation.z);
			}
		}
	}

	public getArrivals() {
		const newArrivals: Arrival[] = [];
		this.arrivals.forEach(arrival => {
			if (newArrivals.length < 10 && (arrival.isContinuous || arrival.getDepartureTime() >= 0) && (this.filterArrivalRoutes.length == 0 || this.filterArrivalRoutes.includes(arrival.key)) && (this.filterArrivalShowTerminating || !arrival.isTerminating)) {
				newArrivals.push(arrival);
			}
		});
		return newArrivals;
	}

	public getHasTerminating() {
		return this.hasTerminating;
	}

	public updateArrivalFilter(filterArrivalRoutes: string[], filterArrivalShowTerminating: boolean) {
		this.resetArrivalFilter();
		filterArrivalRoutes.forEach(routeKey => this.filterArrivalRoutes.push(routeKey));
		this.filterArrivalShowTerminating = filterArrivalShowTerminating;
	}

	public resetArrivalFilter() {
		this.filterArrivalRoutes.length = 0;
		this.filterArrivalShowTerminating = false;
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
	readonly routeColor: string;
	readonly routeColorInt: number;
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
		this.routeColor = dataResponse.routeColor.toString(16);
		this.routeColorInt = dataResponse.routeColor;
		const tempRouteType = dataService.getAllRoutes().find(route => route.name === dataResponse.routeName)?.type;
		this.routeTypeIcon = tempRouteType == undefined ? "" : ROUTE_TYPES[tempRouteType].icon;
		this.circularState = dataResponse.circularState;
		this.platformName = dataResponse.platformName;
		this.cars = dataResponse.cars.map(car => car.vehicleId);
		this.arrival = dataResponse.arrival === 0 ? 0 : dataResponse.arrival + dataService.getTimeOffset();
		this.departure = dataResponse.departure === 0 ? 0 : dataResponse.departure + dataService.getTimeOffset();
		this.isContinuous = this.arrival === 0;
		this.key = SimplifyRoutesPipe.getRouteId({name: this.routeName, number: this.routeNumber, color: this.routeColor});
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
