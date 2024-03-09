import {Pipe, PipeTransform} from "@angular/core";
import {Station} from "../service/data.service";

@Pipe({
	name: "simplifyStations",
	pure: true,
	standalone: true
})
export class SimplifyStationsPipe implements PipeTransform {

	transform(stations: Station[]): { color: string, name: string }[] {
		return stations.map(station => ({color: station.color, name: station.name}));
	}
}
