import {Pipe, PipeTransform} from "@angular/core";
import {RouteExtended} from "../service/data.service";
import {ROUTE_TYPES} from "../data/routeType";

@Pipe({
	name: "simplifyRoutes",
	pure: true,
	standalone: true,
})
export class SimplifyRoutesPipe implements PipeTransform {

	transform(routes: RouteExtended[]): { id: string, icons: string[], color: string, name: string, number: string }[] {
		const newRoutes: { [key: string]: { id: string, icons: string[], color: string, name: string, number: string } } = {};
		routes.forEach(route => {
			const id = SimplifyRoutesPipe.getRouteId(route);
			newRoutes[id] = {id, icons: [ROUTE_TYPES[route.type].icon, ""], color: route.color, name: route.name.split("||")[0], number: route.number};
		});
		return Object.values(newRoutes);
	}

	public static getRouteId(route: { color: string, name: string, number: string }) {
		return `${route.color}_${route.name.split("||")[0]}_${route.number}`;
	}

	public static sortRoutes(routes: { name: string, number: string, color: string, textLineCount?: number }[]) {
		routes.sort((route1, route2) => {
			const linesCompare = route1.textLineCount === undefined || route2.textLineCount === undefined ? 0 : route1.textLineCount - route2.textLineCount;
			if (linesCompare == 0) {
				const numberCompare = route1.number.localeCompare(route2.number);
				return numberCompare == 0 ? `${SimplifyRoutesPipe.getRouteId(route1)}_${route1.name}`.localeCompare(`${SimplifyRoutesPipe.getRouteId(route2)}_${route2.name}`) : numberCompare;
			} else {
				return linesCompare;
			}
		});
	}

	public static getCircularStateIcon(circularState: "NONE" | "CLOCKWISE" | "ANTICLOCKWISE") {
		return circularState === "CLOCKWISE" ? "rotate_right" : circularState === "ANTICLOCKWISE" ? "rotate_left" : "";
	}
}
