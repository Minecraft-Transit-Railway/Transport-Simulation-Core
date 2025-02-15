import {Component} from "@angular/core";
import {SearchComponent} from "../search/search.component";
import {DirectionsService} from "../../service/directions.service";
import {MatProgressSpinnerModule} from "@angular/material/progress-spinner";
import {RouteDisplayComponent} from "../route-display/route-display.component";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatDatePipe} from "../../pipe/formatDatePipe";
import {MatExpansionModule} from "@angular/material/expansion";
import {MatIconModule} from "@angular/material/icon";
import {FormatTimePipe} from "../../pipe/formatTimePipe";
import {MatSliderModule} from "@angular/material/slider";
import {MatFormField, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {ReactiveFormsModule} from "@angular/forms";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {Route} from "../../entity/route";

@Component({
	selector: "app-directions",
	imports: [
		SearchComponent,
		MatProgressSpinnerModule,
		RouteDisplayComponent,
		FormatNamePipe,
		DataListEntryComponent,
		FormatDatePipe,
		MatExpansionModule,
		MatIconModule,
		MatSliderModule,
		MatFormField,
		MatInput,
		MatLabel,
		ReactiveFormsModule,
	],
	templateUrl: "./directions.component.html",
	styleUrl: "./directions.component.css",
})
export class DirectionsComponent {
	private startStationId?: string;
	private endStationId?: string;
	private maxWalkingDistanceString = "";

	constructor(private readonly directionsService: DirectionsService, private readonly formatNamePipe: FormatNamePipe, private readonly formatTimePipe: FormatTimePipe) {
		directionsService.directionsPanelOpened.subscribe(() => setTimeout(() => this.checkStatus(), 0));
	}

	onClickStation(stationId: string | undefined, isStart: boolean) {
		if (isStart) {
			this.startStationId = stationId;
		} else {
			this.endStationId = stationId;
		}

		this.checkStatus();
	}

	updateMaxWalkingDistance(value: string) {
		this.maxWalkingDistanceString = value;
		this.checkStatus();
	}

	getDefaultMaxWalkingDistance() {
		return this.directionsService.defaultMaxWalkingDistance;
	}

	getDirections() {
		return this.directionsService.getDirections();
	}

	isLoading() {
		return this.directionsService.isLoading();
	}

	noDirections() {
		return this.startStationId && this.endStationId && this.startStationId !== this.endStationId && this.directionsService.getDirections().length === 0;
	}

	getRouteName(route: Route) {
		return `${this.formatNamePipe.transform(route.name.split("||")[0])} ${this.formatNamePipe.transform(route.number)}`;
	}

	getRouteDestination(route: Route) {
		return route.circularState === "NONE" ? this.formatNamePipe.transform(route.routePlatforms[route.routePlatforms.length - 1].station.name) : "";
	}

	getRouteColor(index: number) {
		return this.getDirections()[index]?.route?.color ?? -1;
	}

	getDuration(direction: { startTime: number, endTime: number }) {
		return this.formatTimePipe.transform(Math.round((direction.endTime - direction.startTime) / 1000), "");
	}

	getDistance(direction: { distance: number }) {
		return Math.round(direction.distance / 100) / 10;
	}

	getCircularIcon(route: Route) {
		return SimplifyRoutesPipe.getCircularStateIcon(route.circularState);
	}

	private checkStatus() {
		if (this.startStationId && this.endStationId && this.startStationId !== this.endStationId) {
			this.directionsService.selectStations(this.startStationId, this.endStationId, this.maxWalkingDistanceString);
		} else {
			this.directionsService.clear();
		}
	}
}
