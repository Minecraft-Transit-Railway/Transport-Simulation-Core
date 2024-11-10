import {Component, EventEmitter, Output, ViewChild} from "@angular/core";
import {MapComponent} from "../map/map.component";
import {MatButtonModule} from "@angular/material/button";
import {MatIconModule} from "@angular/material/icon";
import {MatSidenav, MatSidenavModule} from "@angular/material/sidenav";
import {MatToolbarModule} from "@angular/material/toolbar";
import {StationService} from "../../service/station.service";
import {DirectionsService} from "../../service/directions.service";
import {MatTooltipModule} from "@angular/material/tooltip";

@Component({
	selector: "app-side",
	standalone: true,
	imports: [
		MapComponent,
		MatIconModule,
		MatButtonModule,
		MatSidenavModule,
		MatToolbarModule,
		MatTooltipModule,
	],
	templateUrl: "./side.component.html",
	styleUrl: "./side.component.css",
})
export class SideComponent {
	@ViewChild(MatSidenav) private readonly sidenav!: MatSidenav;
	@Output() closed = new EventEmitter<void>;

	constructor(private readonly stationService: StationService, private readonly directionsService: DirectionsService) {
	}

	open() {
		this.sidenav.open().then();
	}

	close() {
		this.sidenav.close().then();
	}

	onCloseMenu() {
		this.sidenav.close().then();
		this.closed.emit();
	}
}
