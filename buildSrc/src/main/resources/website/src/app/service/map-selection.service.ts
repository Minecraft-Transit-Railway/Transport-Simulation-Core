import {EventEmitter, Injectable} from "@angular/core";

@Injectable({providedIn: "root"})
export class MapSelectionService {
	public readonly selectedStationConnections: { stationIds: [string, string], routeColor: number }[] = [];
	public readonly selectedStations: string[] = [];
	public readonly updateSelection = new EventEmitter<void>();
	private key: string = "";

	public select(key: string) {
		this.key = key;
		this.updateSelection.emit();
	}

	public reset(key: string) {
		if (this.key === key) {
			this.selectedStationConnections.length = 0;
			this.selectedStations.length = 0;
			this.updateSelection.emit();
		}
	}
}
