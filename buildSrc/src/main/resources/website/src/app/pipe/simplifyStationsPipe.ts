import {Pipe, PipeTransform} from "@angular/core";
import {Station} from "../entity/station";

@Pipe({
	name: "simplifyStations",
	pure: true,
	standalone: true,
})
export class SimplifyStationsPipe implements PipeTransform {

	transform(stations: Station[]): { key: string, icons: string[], color: number, name: string, number: string }[] {
		return stations.map(station => ({key: station.id, icons: station.getIcons(), color: station.color, name: station.name, number: ""}));
	}
}
