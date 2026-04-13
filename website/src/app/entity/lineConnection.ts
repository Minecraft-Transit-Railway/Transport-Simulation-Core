export interface LineConnection {
	readonly lineConnectionParts: LineConnectionPart[];
	readonly direction1: 0 | 1 | 2 | 3;
	readonly direction2: 0 | 1 | 2 | 3;
	readonly x1: number;
	readonly x2: number;
	readonly z1: number;
	readonly z2: number;
	readonly stationId1: string;
	readonly stationId2: string;
	readonly length: number;
	readonly relativeLength: number;
}

export interface LineConnectionPart {
	readonly color: string;
	readonly oneWay: number;
	readonly offset1: number;
	readonly offset2: number;
}
