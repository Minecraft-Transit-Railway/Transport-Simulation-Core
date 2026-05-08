import {Station} from "./station";

export interface StationForMap {
	readonly station: Station;
	readonly rotate: boolean;
	readonly routeCount: number;
	readonly width: number;
	readonly height: number;
}
