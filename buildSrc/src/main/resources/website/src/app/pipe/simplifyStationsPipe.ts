import {Pipe, PipeTransform} from "@angular/core";
import {StationWithPosition} from "../service/data.service";
import {ROUTE_TYPES} from "../data/routeType";

@Pipe({
	name: "simplifyStations",
	pure: true,
	standalone: true,
})
export class SimplifyStationsPipe implements PipeTransform {

	transform(stations: StationWithPosition[]): { id: string, icons: string[], color: string, name: string, number: string }[] {
		return stations.map(station => ({id: station.id, icons: station.types.map(type => ROUTE_TYPES[type].icon), color: station.color, name: station.name, number: ""}));
	}
}
