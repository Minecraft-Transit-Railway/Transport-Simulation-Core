import {EventEmitter, inject, Injectable} from "@angular/core";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {Route} from "../entity/route";
import {DimensionService} from "./dimension.service";
import {ROUTE_TYPES} from "../data/routeType";
import {MapSelectionService} from "./map-selection.service";
import {HttpClient} from "@angular/common/http";
import {DirectionsResponseDTO} from "../entity/generated/directionsResponse";
import {DirectionsRequestDTO} from "../entity/generated/directionsRequest";
import {MapDataService} from "./map-data.service";
import {Station} from "../entity/station";
import {ClientsService} from "./clients.service";

const REFRESH_INTERVAL = 3000;

@Injectable({providedIn: "root"})
export class DirectionsService extends SelectableDataServiceBase<{ currentTime: number, data: DirectionsResponseDTO }, { directionsRequest: DirectionsRequestDTO, startStationId?: string, endStationId?: string }> {
	public readonly directionsPanelOpened = new EventEmitter<{ stationDetails?: { stationId: string, isStartStation: boolean }, clientDetails?: { clientId: string, isStartClient: boolean } } | undefined>();
	public readonly defaultMaxWalkingDistance = 250;
	private readonly newDirections: {
		startStation?: Station,
		endStation?: Station,
		startPlatformName?: string,
		endPlatformName?: string,
		intermediateStations: Station[],
		route?: Route,
		icon: string,
		startTime: number,
		endTime: number,
		distance: number,
	}[] = [];
	private directionsTimeoutId = 0;

	constructor() {
		const httpClient = inject(HttpClient);
		const mapDataService = inject(MapDataService);
		const mapSelectionService = inject(MapSelectionService);
		const clientsService = inject(ClientsService);
		const dimensionService = inject(DimensionService);

		super(selectedData => {
			const {
				startStationId,
				endStationId,
				startPositionX,
				startPositionY,
				startPositionZ,
				endPositionX,
				endPositionY,
				endPositionZ,
				startClientId,
				endClientId,
			} = JSON.parse(selectedData);
			return (startStationId || startClientId) && (endStationId || endClientId) ? {
				directionsRequest: {
					startPositionX: startClientId ? undefined : startPositionX,
					startPositionY: startClientId ? undefined : startPositionY,
					startPositionZ: startClientId ? undefined : startPositionZ,
					startClientId: startClientId ? startClientId : undefined,
					endPositionX: endClientId ? undefined : endPositionX,
					endPositionY: endClientId ? undefined : endPositionY,
					endPositionZ: endClientId ? undefined : endPositionZ,
					endClientId: endClientId ? endClientId : undefined,
					startTime: 0,
				},
				startStationId,
				endStationId,
			} : undefined;
		}, () => {
			this.newDirections.length = 0;
			mapSelectionService.reset("directions");
			clearTimeout(this.directionsTimeoutId);
		}, ({directionsRequest}) => httpClient.post<{ currentTime: number, data: DirectionsResponseDTO }>(this.getUrl("directions"), JSON.stringify(directionsRequest)), ({data}) => {
			this.newDirections.length = 0;
			const selectedData = this.getSelectedData();
			const directions = data.connections;

			for (let i = directions[0]?.endStationId === selectedData?.startStationId ? 1 : 0; i < directions.length; i++) {
				const previousDirection = directions[i - 1];
				const thisDirection = directions[i];
				const nextDirection = directions[i + 1];
				const startStation = thisDirection.startStationId ? mapDataService.stations.find(({id}) => id === thisDirection.startStationId) : undefined;
				const endStation = thisDirection.endStationId ? mapDataService.stations.find(({id}) => id === thisDirection.endStationId) : undefined;
				const startPlatformName = thisDirection.startPlatformName ? thisDirection.startPlatformName : undefined;
				const endPlatformName = thisDirection.endPlatformName ? thisDirection.endPlatformName : undefined;
				const route = thisDirection.routeId ? mapDataService.routes.find(({id}) => id === thisDirection.routeId) : undefined;

				if (i > 0 && previousDirection.routeId === thisDirection.routeId) {
					const lastNewDirection = this.newDirections[this.newDirections.length - 1];
					if (lastNewDirection.endStation) {
						lastNewDirection.intermediateStations.push(lastNewDirection.endStation);
					}
					lastNewDirection.endStation = endStation;
					lastNewDirection.endPlatformName = endPlatformName;
					lastNewDirection.endTime = thisDirection.endTime;
				} else {
					const offsetTime = i === 0 && !route && nextDirection ? nextDirection.startTime - thisDirection.endTime : 0;
					this.newDirections.push({
						startStation,
						endStation,
						startPlatformName,
						endPlatformName,
						intermediateStations: [],
						route,
						icon: route ? ROUTE_TYPES[route.type].icon : "",
						startTime: thisDirection.startTime + offsetTime,
						endTime: thisDirection.endTime + offsetTime,
						distance: thisDirection.walkingDistance,
					});
				}

				if (this.newDirections[this.newDirections.length - 1]?.endStation?.id === selectedData?.endStationId) {
					break;
				}
			}

			const firstDirection = this.newDirections[0];
			const lastDirection = this.newDirections[this.newDirections.length - 1];
			const directionsRequest = selectedData ? selectedData.directionsRequest : undefined;
			if (firstDirection && !firstDirection.startStation) {
				const startStation = mapDataService.stations.find(station => station.id === selectedData?.startStationId);
				if (startStation) {
					firstDirection.startStation = startStation;
				} else {
					const name = clientsService.getClient(directionsRequest?.startClientId)?.name;
					if (name) {
						firstDirection.startStation = {name, color: 0, connections: [], getIcons: () => [], id: "", routes: [], x: 0, y: 0, z: 0, zone1: 0, zone2: 0, zone3: 0};
					}
				}
			}
			if (lastDirection && !lastDirection.endStation) {
				const endStation = mapDataService.stations.find(station => station.id === selectedData?.endStationId);
				if (endStation) {
					lastDirection.endStation = endStation;
				} else {
					const name = clientsService.getClient(directionsRequest?.endClientId)?.name;
					if (name) {
						lastDirection.endStation = {name, color: 0, connections: [], getIcons: () => [], id: "", routes: [], x: 0, y: 0, z: 0, zone1: 0, zone2: 0, zone3: 0};
					}
				}
			}
		}, REFRESH_INTERVAL, dimensionService);
	}

	public selectData(startStation: Station | undefined, endStation: Station | undefined, startClientId: string | undefined, endClientId: string | undefined, maxWalkingDistanceString: string) {
		const key = JSON.stringify({
			startStationId: startStation?.id,
			endStationId: endStation?.id,
			startPositionX: startStation?.x,
			startPositionY: startStation?.y,
			startPositionZ: startStation?.z,
			endPositionX: endStation?.x,
			endPositionY: endStation?.y,
			endPositionZ: endStation?.z,
			startClientId,
			endClientId,
			maxWalkingDistance: parseInt(maxWalkingDistanceString),
		});
		this.select(key);
		this.fetchData(key);
	}

	public getDirections() {
		return this.newDirections;
	}
}
