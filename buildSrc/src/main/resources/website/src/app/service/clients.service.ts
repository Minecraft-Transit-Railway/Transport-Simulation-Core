import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {DimensionService} from "./dimension.service";
import {DataServiceBase} from "./data-service-base";
import {MapDataService} from "./map-data.service";
import {ClientsDTO} from "../entity/generated/clients";
import {Station} from "../entity/station";
import {Route} from "../entity/route";
import {setIfUndefined} from "../data/utilities";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class ClientsService extends DataServiceBase<{ data: ClientsDTO }> {
	public readonly allClients: { id: string, name: string, rawX: number, rawZ: number }[] = [];
	public readonly allClientsNotInStationOrRoute: { id: string, name: string, rawX: number, rawZ: number }[] = [];
	private clientGroupsForStation: Record<string, {
		clients: {
			id: string,
			name: string,
		}[],
		x: number,
		z: number,
		station?: Station,
		route?: Route,
		routeStationId1: string,
		routeStationId2: string,
	}> = {};
	private clientGroupsForRoute: Record<string, {
		clients: {
			id: string,
			name: string,
		}[],
		x: number,
		z: number,
		station?: Station,
		route?: Route,
		routeStationId1: string,
		routeStationId2: string,
	}> = {};
	private readonly nameCache: Record<string, string> = {};

	constructor(private readonly httpClient: HttpClient, mapDataService: MapDataService, dimensionService: DimensionService) {
		super(() => this.httpClient.get<{ data: ClientsDTO }>(this.getUrl("clients")), ({data}) => {
			this.allClients.length = 0;
			this.allClientsNotInStationOrRoute.length = 0;
			this.clientGroupsForStation = {};
			this.clientGroupsForRoute = {};

			data.clients.forEach(clientDTO => {
				const client = {id: clientDTO.id, name: "", rawX: clientDTO.x, rawZ: clientDTO.z};
				if (client.id in this.nameCache) {
					client.name = this.nameCache[client.id];
				} else {
					this.httpClient.get<{ username: string }>(`https://www.mc-heads.net/json/get_user?search&u=${client.id}`).subscribe(({username}) => {
						this.nameCache[client.id] = username;
						client.name = username;
					});
				}
				this.allClients.push(client);

				const route = clientDTO.routeId ? mapDataService.routes.find(route => route.id === clientDTO.routeId) : undefined;
				const station = (!route || route.routePlatforms.some(({station}) => station.id === clientDTO.stationId)) && clientDTO.stationId ? mapDataService.stations.find(station => station.id === clientDTO.stationId) : undefined;
				if (station) {
					setIfUndefined(this.clientGroupsForStation, clientDTO.stationId, () => ({
						clients: [],
						x: station.x,
						z: station.z,
						station,
						route,
						routeStationId1: "",
						routeStationId2: "",
					}));
					this.clientGroupsForStation[clientDTO.stationId].clients.push(client);
				} else if (route) {
					const reverse = clientDTO.routeStationId1 > clientDTO.routeStationId2;
					const newRouteStationId1 = reverse ? clientDTO.routeStationId2 : clientDTO.routeStationId1;
					const newRouteStationId2 = reverse ? clientDTO.routeStationId1 : clientDTO.routeStationId2;
					const key = ClientsService.getRouteConnectionKey(newRouteStationId1, newRouteStationId2, route.color);
					setIfUndefined(this.clientGroupsForRoute, key, () => ({
						clients: [],
						x: clientDTO.x,
						z: clientDTO.z,
						station: undefined,
						route,
						routeStationId1: newRouteStationId1,
						routeStationId2: newRouteStationId2,
					}));
					this.clientGroupsForRoute[key].clients.push(client);
				} else {
					this.allClientsNotInStationOrRoute.push(client);
				}
			});
		}, REFRESH_INTERVAL, dimensionService);
		mapDataService.dataProcessed.subscribe(() => this.fetchData(""));
	}

	public getClientGroupsForStation() {
		return this.clientGroupsForStation;
	}

	public getClientGroupsForRoute() {
		return this.clientGroupsForRoute;
	}

	public static getRouteConnectionKey(stationId1: string, stationId2: string, color: number) {
		return `${stationId1}_${stationId2}_${color}`;
	}
}
