import {Pipe, PipeTransform} from "@angular/core";
import {Station} from "../entity/station";
import {SearchData} from "../entity/searchData";

@Pipe({
	name: "simplifyStations",
	pure: true,
	standalone: true,
})
export class SimplifyStationsPipe implements PipeTransform {

	transform(stations: Station[]): SearchData[] {
		return stations.map(station => ({key: station.id, icons: station.getIcons(), color: station.color, name: station.name, number: "", isStation: true}));
	}
}
