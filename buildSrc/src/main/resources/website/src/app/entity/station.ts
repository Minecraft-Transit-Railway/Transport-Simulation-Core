import {Route} from "./route";
import {StationDTO} from "./generated/station";
import {ROUTE_TYPES} from "../data/routeType";

export class Station {
	public readonly id: string;
	public readonly name: string;
	public readonly color: number;
	public readonly zone1: number;
	public readonly zone2: number;
	public readonly zone3: number;
	public readonly x: number;
	public readonly y: number;
	public readonly z: number;
	public readonly connections: Station[] = [];
	public readonly routes: Route[] = [];

	constructor(
		stationDTO: StationDTO,
		x: number,
		y: number,
		z: number,
	) {
		this.id = stationDTO.id;
		this.name = stationDTO.name;
		this.color = stationDTO.color;
		this.zone1 = stationDTO.zone1;
		this.zone2 = stationDTO.zone2;
		this.zone3 = stationDTO.zone3;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public readonly getIcons = (predicate?: (type: string) => boolean) => {
		const icons: string[] = [];
		Object.entries(ROUTE_TYPES).forEach(([routeTypeKey, routeType]) => {
			if ((predicate === undefined || predicate(routeTypeKey)) && this.routes.some(({type}) => type === routeTypeKey)) {
				icons.push(routeType.icon);
			}
		});
		return icons;
	};
}
