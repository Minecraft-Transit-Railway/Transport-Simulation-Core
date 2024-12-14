import {Station} from "./station";

export class StationForMap {
	constructor(
		public readonly station: Station,
		public readonly rotate: boolean,
		public readonly routeCount: number,
		public readonly width: number,
		public readonly height: number,
	) {
	}
}
