import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {MapDataService} from "./map-data.service";
import {SplitNamePipe} from "../pipe/splitNamePipe";
import {ROUTE_TYPES} from "../data/routeType";
import {DimensionService} from "./dimension.service";
import {SimplifyRoutesPipe} from "../pipe/simplifyRoutesPipe";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {Station} from "../entity/station";
import {arrRemove} from "rxjs/internal/util/arrRemove";

const REFRESH_INTERVAL = 3000;
const MAX_ARRIVALS = 5;

@Injectable({providedIn: "root"})
export class StationService extends SelectableDataServiceBase<{ currentTime: number, data: { arrivals: DataResponse[] } }, Station> {
	public readonly arrivalsRoutes: { key: string, name: string, number: string, color: number, textLineCount: number, typeIcon: string }[] = [];
	public readonly routesAtStation: { name: string, variations: string[], number: string, color: number, typeIcon: string }[] = [];
	private readonly arrivals: Arrival[] = [];
	private hasTerminating = false;
	private readonly filterArrivalRoutes: string[] = [];
	private filterArrivalShowTerminating = false;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: MapDataService, private readonly splitNamePipe: SplitNamePipe, dimensionService: DimensionService) {
		super(stationId => this.dataService.stations.find(station => station.id === stationId), () => {
			this.arrivals.length = 0;
			this.arrivalsRoutes.length = 0;
			this.routesAtStation.length = 0;
		}, selectedStation => this.httpClient.post<{ currentTime: number, data: { arrivals: DataResponse[] } }>(this.getUrl("arrivals"), JSON.stringify({
			stationIdsHex: [selectedStation.id],
			maxCountPerPlatform: MAX_ARRIVALS,
		})), (data: { currentTime: number, data: { arrivals: DataResponse[] } }) => {
			this.arrivals.length = 0;
			const routes: Record<string, { key: string, name: string, number: string, color: number, textLineCount: number, typeIcon: string }> = {};
			this.hasTerminating = false;

			data.data.arrivals.forEach(arrival => {
				const newArrival = new Arrival(this.dataService, Date.now() - data.currentTime, arrival);
				this.arrivals.push(newArrival);
				routes[newArrival.key] = {
					key: newArrival.key,
					name: newArrival.routeName,
					number: newArrival.routeNumber,
					color: newArrival.routeColor,
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
		}, REFRESH_INTERVAL, dimensionService);
		setInterval(() => this.arrivals.forEach(arrival => arrival.calculateValues()), 100);
	}

	public setStation(stationId: string, zoomToStation: boolean) {
		this.select(stationId);
		const newRoutes: Record<string, { name: string, variations: string[], number: string, color: number, typeIcon: string }> = {};
		this.dataService.routes.forEach(({name, number, color, type, routePlatforms}) => {
			if (routePlatforms.some(routePlatform => routePlatform.station.id === this.getSelectedData()?.id)) {
				const key = SimplifyRoutesPipe.getRouteKey({name, number, color});
				const variation = name.split("||")[1];
				if (key in newRoutes) {
					newRoutes[key].variations.push(variation);
				} else {
					newRoutes[key] = {name: name.split("||")[0], variations: [variation], number, color, typeIcon: ROUTE_TYPES[type].icon};
				}
			}
		});
		Object.values(newRoutes).forEach(route => {
			route.variations.sort();
			this.routesAtStation.push(route);
		});
		SimplifyRoutesPipe.sortRoutes(this.routesAtStation);

		this.hasTerminating = false;
		this.fetchData(stationId);
		this.resetArrivalFilter();

		const selectedStation = this.getSelectedData();
		if (selectedStation) {
			if (selectedStation.routes.every(({type}) => this.dataService.routeTypeVisibility[type] === "HIDDEN")) {
				selectedStation.routes.forEach(({type}) => this.dataService.routeTypeVisibility[type] = "SOLID");
				this.dataService.updateData();
			}
			if (zoomToStation) {
				this.dataService.animateMap.emit({x: selectedStation.x, z: selectedStation.z});
			}
		}
	}

	public getArrivals() {
		const newArrivals: Arrival[] = [];
		this.arrivals.forEach(arrival => {
			if (newArrivals.length < 10 && (arrival.isContinuous || arrival.getDepartureTime() >= 0) && (this.filterArrivalRoutes.length === 0 || this.filterArrivalRoutes.includes(arrival.key)) && (this.filterArrivalShowTerminating || !arrival.isTerminating)) {
				newArrivals.push(arrival);
			}
		});
		return newArrivals;
	}

	public getHasTerminating() {
		return this.hasTerminating;
	}

	public updateArrivalFilter(filterArrivalShowTerminating: boolean, toggleRouteKey?: string) {
		if (toggleRouteKey) {
			if (this.filterArrivalRoutes.includes(toggleRouteKey)) {
				arrRemove(this.filterArrivalRoutes, toggleRouteKey);
			} else {
				this.filterArrivalRoutes.push(toggleRouteKey);
			}
		}
		this.filterArrivalShowTerminating = filterArrivalShowTerminating;
	}

	public routeFiltered(routeKey: string) {
		return this.filterArrivalRoutes.includes(routeKey);
	}

	public resetArrivalFilter() {
		this.filterArrivalRoutes.length = 0;
		this.filterArrivalShowTerminating = false;
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
	private arrivalDifference = 0;
	private departureDifference = 0;

	constructor(dataService: MapDataService, timeOffset: number, dataResponse: DataResponse) {
		this.destination = dataResponse.destination;
		this.deviation = dataResponse.deviation;
		this.realtime = dataResponse.realtime;
		this.departureIndex = dataResponse.departureIndex;
		this.isTerminating = dataResponse.isTerminating;
		this.routeName = dataResponse.routeName.split("||")[0];
		this.routeNumber = dataResponse.routeNumber;
		this.routeColor = dataResponse.routeColor;
		const tempRouteType = dataService.routes.find(route => route.name === dataResponse.routeName)?.type;
		this.routeTypeIcon = tempRouteType === undefined ? "" : ROUTE_TYPES[tempRouteType].icon;
		this.circularState = dataResponse.circularState;
		this.platformName = dataResponse.platformName;
		this.cars = dataResponse.cars.map(car => car.vehicleId);
		this.arrival = dataResponse.arrival === 0 ? 0 : dataResponse.arrival + timeOffset;
		this.departure = dataResponse.departure === 0 ? 0 : dataResponse.departure + timeOffset;
		this.isContinuous = this.arrival === 0;
		this.key = SimplifyRoutesPipe.getRouteKey({name: this.routeName, number: this.routeNumber, color: this.routeColor});
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
		return SimplifyRoutesPipe.getDeviationString(this.realtime, this.deviation);
	}

	calculateValues() {
		this.arrivalDifference = Math.round((this.arrival - new Date().getTime()) / 1000);
		this.departureDifference = Math.round((this.departure - new Date().getTime()) / 1000);
	}
}
