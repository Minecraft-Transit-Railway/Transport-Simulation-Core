import {Pipe, PipeTransform} from "@angular/core";
import {Station} from "../service/data.service";

@Pipe({
	name: "simplifyStations",
	pure: true,
	standalone: true
})
export class SimplifyStationsPipe implements PipeTransform {

	transform(stations: Station[]): { id: string, color: string, name: string }[] {
		return stations.map(station => ({id: station.id, color: station.color, name: station.name}));
	}
}
