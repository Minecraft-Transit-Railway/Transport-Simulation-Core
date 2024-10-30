import {Component, EventEmitter, Output, ViewChild} from "@angular/core";
import {MapComponent} from "../map/map.component";
import {MatIconButton} from "@angular/material/button";
import {MatIcon} from "@angular/material/icon";
import {MatSidenav, MatSidenavContainer, MatSidenavContent} from "@angular/material/sidenav";
import {MatToolbar} from "@angular/material/toolbar";
import {StationService} from "../../service/station.service";
import {DirectionsService} from "../../service/directions.service";

@Component({
	selector: "app-side",
	standalone: true,
	imports: [
		MapComponent,
		MatIcon,
		MatIconButton,
		MatSidenav,
		MatSidenavContainer,
		MatSidenavContent,
		MatToolbar,
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
