import {LineConnectionPart} from "./lineConnectionPart";
import {Type} from "class-transformer";

export class LineConnection {
	@Type(() => LineConnectionPart)
	public readonly lineConnectionParts: LineConnectionPart[] = [];
	public readonly direction1: 0 | 1 | 2 | 3 = 0;
	public readonly direction2: 0 | 1 | 2 | 3 = 0;
	public readonly x1: number = 0;
	public readonly x2: number = 0;
	public readonly z1: number = 0;
	public readonly z2: number = 0;
	public readonly length: number = 0;
}
