import {Pipe, PipeTransform} from "@angular/core";
import {Route} from "../service/data.service";

@Pipe({
	name: "simplifyRoutes",
	pure: true,
	standalone: true,
})
export class SimplifyRoutesPipe implements PipeTransform {

	transform(routes: Route[]): { id: string, color: string, name: string }[] {
		return routes.map(route => ({id: route.color, color: route.color, name: route.name}));
	}
}
