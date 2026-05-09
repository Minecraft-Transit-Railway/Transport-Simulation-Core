import {inject, Injectable, signal} from "@angular/core";

import {of} from "rxjs";

import {Route} from "../entity/route";
import {Station} from "../entity/station";
import {SLOW_REFRESH_INTERVAL_MILLIS} from "../utility/refresh.constants";
import {ClientsService} from "./clients.service";
import {DimensionService} from "./dimension.service";
import {MapDataService} from "./map-data.service";
import {SelectableDataServiceBase} from "./selectable-data-service-base";

@Injectable({providedIn: "root"})
export class ClientService extends SelectableDataServiceBase<string, string> {
	private readonly mapDataService = inject(MapDataService);
	private readonly clientsService = inject(ClientsService);

	public readonly client = signal<{
		name: string,
		rawX: number,
		rawZ: number,
		station?: Station,
		route?: Route,
		routeStation1?: Station,
		routeStation2?: Station,
	} | undefined>(undefined);

	constructor() {
		const dimensionService = inject(DimensionService);

		super(clientId => clientId, () => {
			// empty
		}, clientId => of(clientId), () => {
			//empty
		}, SLOW_REFRESH_INTERVAL_MILLIS, dimensionService);
		this.clientsService.dataProcessed.subscribe(() => this.updateClient());
	}

	public setClient(clientId: string) {
		this.select(clientId);
		this.mapDataService.animateClient.next(clientId);
		this.updateClient();
	}

	private updateClient() {
		this.client.set(this.clientsService.getClient(this.selectedData()));
	}
}
