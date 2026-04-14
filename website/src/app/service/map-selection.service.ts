import {Injectable} from "@angular/core";
import {Subject} from "rxjs";

@Injectable({providedIn: "root"})
export class MapSelectionService {
	public readonly selectedStationConnections: { stationIds: [string, string], routeColor: number }[] = [];
	public readonly selectedStations: string[] = [];
	public readonly updateSelection = new Subject<void>();
	private key = "";

	public select(key: string) {
		this.key = key;
		this.updateSelection.next();
	}

	public reset(key: string) {
		if (this.key === key) {
			this.selectedStationConnections.length = 0;
			this.selectedStations.length = 0;
			this.updateSelection.next();
		}
	}
}
