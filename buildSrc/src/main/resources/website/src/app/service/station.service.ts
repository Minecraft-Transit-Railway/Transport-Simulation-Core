import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, StationWithPosition} from "./data.service";

@Injectable({providedIn: "root"})
export class StationService {

	private selectedStation?: StationWithPosition;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService) {
	}

	public getSelectedStation() {
		return this.selectedStation;
	}

	public setStation(stationId: string) {
		this.selectedStation = this.dataService.getAllStations().find(station => station.id === stationId);
	}

	public clear() {
		this.selectedStation = undefined;
	}

	private getArrivals() {

	}
}
