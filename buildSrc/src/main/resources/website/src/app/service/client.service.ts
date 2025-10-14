import {inject, Injectable} from "@angular/core";
import {MapDataService} from "./map-data.service";
import {DimensionService} from "./dimension.service";
import {SelectableDataServiceBase} from "./selectable-data-service-base";
import {of} from "rxjs";
import {ClientsService} from "./clients.service";
import {Station} from "../entity/station";
import {Route} from "../entity/route";

const REFRESH_INTERVAL = 30000;

@Injectable({providedIn: "root"})
export class ClientService extends SelectableDataServiceBase<string, string> {
	private readonly mapDataService = inject(MapDataService);
	private readonly clientsService = inject(ClientsService);

	private client?: {
		name: string,
		rawX: number,
		rawZ: number,
		station?: Station,
		route?: Route,
		routeStation1?: Station,
		routeStation2?: Station,
	};

	constructor() {
		const dimensionService = inject(DimensionService);

		super(clientId => clientId, () => {
		}, clientId => of(clientId), () => {
		}, REFRESH_INTERVAL, dimensionService);

		this.clientsService.dataProcessed.subscribe(() => this.updateClient());
	}

	public setClient(clientId: string) {
		this.select(clientId);
		this.mapDataService.animateClient.emit(clientId);
		this.updateClient();
	}

	public getClient() {
		return this.client;
	}

	private updateClient() {
		this.client = this.clientsService.getClient(this.getSelectedData());
	}
}
