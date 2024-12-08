import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DataService, RouteExtended} from "./data.service";
import {ServiceBase} from "./service";
import {DimensionService} from "./dimension.service";
import {SimplifyRoutesPipe} from "../pipe/simplifyRoutesPipe";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class RouteService extends ServiceBase<{ data: { route: DataResponse[] } }> {
	private readonly selectedRoutes: RouteExtended[] = [];
	private selectedRoute?: RouteExtended;
	private randomSeed = 0;

	constructor(private readonly httpClient: HttpClient, private readonly dataService: DataService, dimensionService: DimensionService) {
		super(() => {
			if (this.selectedRoutes.length > 0) {
				return this.httpClient.post<{ data: { route: DataResponse[] } }>(this.getUrl("route"), JSON.stringify({
					// TODO
				}));
			} else {
				return;
			}
		}, REFRESH_INTERVAL, dimensionService);
	}

	protected override processData(data: { data: { route: DataResponse[] } }) {

	}

	public setRoute(routeId: string) {
		this.selectedRoutes.length = 0;
		this.dataService.getAllRoutes().forEach(route => {
			if (SimplifyRoutesPipe.getRouteId(route) === routeId) {
				this.selectedRoutes.push(route);
			}
		});
		SimplifyRoutesPipe.sortRoutes(this.selectedRoutes);
		this.randomSeed = Math.random();
		this.selectRoute(0);
	}

	public getNames() {
		return this.selectedRoutes.map(route => route.name);
	}

	public getRandomSeed() {
		return this.randomSeed;
	}

	public getSelectedRoute() {
		return this.selectedRoute;
	}

	public selectRoute(index: number) {
		this.selectedRoute = this.selectedRoutes[index] ?? this.selectedRoutes[0];
	}

	public clear() {
		this.selectedRoutes.length = 0;
	}
}

class DataResponse {
	// TODO
}
