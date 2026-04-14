export interface SearchData {
	readonly key: string;
	readonly icons: string[];
	readonly color?: number;
	readonly name: string;
	readonly number: string;
	readonly type: "station" | "route" | "client";
}
