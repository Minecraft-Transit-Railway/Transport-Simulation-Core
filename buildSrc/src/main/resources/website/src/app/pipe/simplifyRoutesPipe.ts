import {Pipe, PipeTransform} from "@angular/core";
import {ROUTE_TYPES} from "../data/routeType";
import {Route} from "../entity/route";
import {SearchData} from "../entity/searchData";

@Pipe({
	name: "simplifyRoutes",
	pure: true,
	standalone: true,
})
export class SimplifyRoutesPipe implements PipeTransform {

	transform(routes: Route[]): SearchData[] {
		const newRoutes: Record<string, SearchData> = {};
		routes.forEach(route => {
			const key = SimplifyRoutesPipe.getRouteKey(route);
			newRoutes[key] = {key, icons: [ROUTE_TYPES[route.type].icon, ""], color: route.color, name: route.name.split("||")[0], number: route.number, isStation: false};
		});
		return Object.values(newRoutes);
	}

	public static getRouteKey(route: { color: number, name: string, number: string }) {
		return `${route.color}_${route.name.split("||")[0]}_${route.number}`;
	}

	public static sortRoutes(routes: { name: string, number: string, color: number, textLineCount?: number }[]) {
		routes.sort((route1, route2) => {
			const linesCompare = route1.textLineCount === undefined || route2.textLineCount === undefined ? 0 : route1.textLineCount - route2.textLineCount;
			if (linesCompare === 0) {
				const numberCompare = route1.number.localeCompare(route2.number);
				return numberCompare === 0 ? `${SimplifyRoutesPipe.getRouteKey(route1)}_${route1.name}`.localeCompare(`${SimplifyRoutesPipe.getRouteKey(route2)}_${route2.name}`) : numberCompare;
			} else {
				return linesCompare;
			}
		});
	}

	public static getCircularStateIcon(circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE") {
		return circularState === "CLOCKWISE" ? "rotate_right" : circularState === "ANTICLOCKWISE" ? "rotate_left" : "";
	}

	public static getDeviationString(realtime: boolean, deviation: number) {
		return realtime ? deviation > 0 ? "delay" : "early" : "Scheduled";
	}
}
