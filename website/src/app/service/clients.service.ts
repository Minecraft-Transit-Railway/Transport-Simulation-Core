import {inject, Injectable, signal} from "@angular/core";
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
	public readonly allClients = signal<{ id: string, name: string, rawX: number, rawZ: number }[]>([]);
	public readonly allClientsNotInStationOrRoute = signal<{ id: string, name: string, rawX: number, rawZ: number }[]>([]);
	public readonly clientGroupsForStation = signal<Record<string, {
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
	}>>({});
	public readonly clientGroupsForRoute = signal<Record<string, {
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
	}>>({});
	private readonly clientCache = signal<Record<string, {
		name: string,
		rawX: number,
		rawZ: number,
		station?: Station,
		route?: Route,
		routeStation1?: Station,
		routeStation2?: Station,
	}>>({});

	constructor() {
		const httpClient = inject(HttpClient);
		const mapDataService = inject(MapDataService);
		const dimensionService = inject(DimensionService);

		super(() => httpClient.get<{ data: ClientsDTO }>(this.getUrl("clients")), ({data}) => {
			const allClients: { id: string, name: string, rawX: number, rawZ: number }[] = [];
			const allClientsNotInStationOrRoute: { id: string, name: string, rawX: number, rawZ: number }[] = [];
			const clientGroupsForStation: Record<string, {
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
			const clientGroupsForRoute: Record<string, {
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
			const clientCache: Record<string, {
				name: string,
				rawX: number,
				rawZ: number,
				station?: Station,
				route?: Route,
				routeStation1?: Station,
				routeStation2?: Station,
			}> = {};

			data.clients.forEach(clientDTO => {
				const client = {id: clientDTO.id, name: clientDTO.name, rawX: clientDTO.x, rawZ: clientDTO.z};
				allClients.push(client);

				const route = clientDTO.routeId ? mapDataService.routes().find(route => route.id === clientDTO.routeId) : undefined;
				const station = (!route || route.routePlatforms.some(({station}) => station.id === clientDTO.stationId)) && clientDTO.stationId ? mapDataService.stations().find(station => station.id === clientDTO.stationId) : undefined;
				if (station) {
					setIfUndefined(clientGroupsForStation, clientDTO.stationId, () => ({
						clients: [],
						x: station.x,
						z: station.z,
						station,
						route,
						routeStationId1: "",
						routeStationId2: "",
					}));
					clientGroupsForStation[clientDTO.stationId].clients.push(client);
				} else if (route) {
					const reverse = clientDTO.routeStationId1 > clientDTO.routeStationId2;
					const newRouteStationId1 = reverse ? clientDTO.routeStationId2 : clientDTO.routeStationId1;
					const newRouteStationId2 = reverse ? clientDTO.routeStationId1 : clientDTO.routeStationId2;
					const key = ClientsService.getRouteConnectionKey(newRouteStationId1, newRouteStationId2, route.color);
					setIfUndefined(clientGroupsForRoute, key, () => ({
						clients: [],
						x: clientDTO.x,
						z: clientDTO.z,
						station: undefined,
						route,
						routeStationId1: newRouteStationId1,
						routeStationId2: newRouteStationId2,
					}));
					clientGroupsForRoute[key].clients.push(client);
				} else {
					allClientsNotInStationOrRoute.push(client);
				}

				clientCache[clientDTO.id] = {
					name: clientDTO.name,
					rawX: clientDTO.x,
					rawZ: clientDTO.z,
					station,
					route,
					routeStation1: route ? mapDataService.stations().find(station => station.id === clientDTO.routeStationId1) : undefined,
					routeStation2: route ? mapDataService.stations().find(station => station.id === clientDTO.routeStationId2) : undefined,
				};
			});

			this.allClients.set(allClients);
			this.allClientsNotInStationOrRoute.set(allClientsNotInStationOrRoute);
			this.clientGroupsForStation.set(clientGroupsForStation);
			this.clientGroupsForRoute.set(clientGroupsForRoute);
			this.clientCache.set(clientCache);
		}, REFRESH_INTERVAL, dimensionService);
		mapDataService.dataProcessed.subscribe(() => this.fetchData(""));
	}

	public getClient(clientId?: string) {
		return clientId ? this.clientCache()[clientId] : undefined;
	}

	public static getRouteConnectionKey(stationId1: string, stationId2: string, color: number) {
		return `${stationId1}_${stationId2}_${color}`;
	}
}
