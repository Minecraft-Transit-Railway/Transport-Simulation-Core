import {Pipe, PipeTransform} from "@angular/core";
import {Route} from "../service/data.service";

@Pipe({
	name: "simplifyRoutes",
	pure: true,
	standalone: true
})
export class SimplifyRoutesPipe implements PipeTransform {

	transform(routes: Route[]): { color: string, name: string }[] {
		return routes.map(route => ({color: route.color, name: route.name}));
	}
}
