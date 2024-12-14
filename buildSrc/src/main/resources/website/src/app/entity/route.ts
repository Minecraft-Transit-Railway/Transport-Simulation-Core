import {Station} from "./station";
import {RouteDTO} from "./generated/route";
import {RouteStationDTO} from "./generated/routeStation";

export class Route {
	public readonly id: string;
	public readonly name: string;
	public readonly color: number;
	public readonly number: string;
	public readonly type: string;
	public readonly circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE";
	public readonly hidden: boolean;
	public readonly depots: string[];
	public readonly routePlatforms: RoutePlatform[] = [];

	constructor(routeDTO: RouteDTO) {
		this.id = routeDTO.id;
		this.name = routeDTO.name;
		this.color = routeDTO.color;
		this.number = routeDTO.number;
		this.type = routeDTO.type;
		this.circularState = routeDTO.circularState;
		this.hidden = routeDTO.hidden;
		this.depots = routeDTO.depots;
	}
}

export class RoutePlatform {
	public readonly station: Station;
	public readonly x: number;
	public readonly y: number;
	public readonly z: number;
	public readonly dwellTime: number;
	public readonly duration: number;

	constructor(
		routeStationDTO: RouteStationDTO,
		station: Station,
		duration: number,
	) {
		this.station = station;
		this.x = routeStationDTO.x;
		this.y = routeStationDTO.y;
		this.z = routeStationDTO.z;
		this.dwellTime = routeStationDTO.dwellTime;
		this.duration = duration;
	}
}
