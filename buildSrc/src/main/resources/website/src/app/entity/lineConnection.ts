export class LineConnection {
	public readonly lineConnectionParts: LineConnectionPart[] = [];
	public readonly direction1: 0 | 1 | 2 | 3 = 0;
	public readonly direction2: 0 | 1 | 2 | 3 = 0;
	public readonly x1: number = 0;
	public readonly x2: number = 0;
	public readonly z1: number = 0;
	public readonly z2: number = 0;
	public readonly length: number = 0;
	public readonly relativeLength: number = 0;
}

export class LineConnectionPart {
	public readonly color: string = "";
	public readonly oneWay: number = 0;
	public readonly offset1: number = 0;
	public readonly offset2: number = 0;
}
