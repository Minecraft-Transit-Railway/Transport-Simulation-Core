import {Component, ViewChild} from "@angular/core";
import {MapComponent} from "../map/map.component";
import {MatIconButton} from "@angular/material/button";
import {MatIcon} from "@angular/material/icon";
import {MatSidenav, MatSidenavContainer, MatSidenavContent} from "@angular/material/sidenav";
import {MatToolbar} from "@angular/material/toolbar";
import {StationService} from "../../service/station.service";

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
	styleUrl: "./side.component.css"
})
export class SideComponent {
	@ViewChild(MatSidenav) private readonly sidenav!: MatSidenav;

	constructor(private readonly stationService: StationService) {
	}

	open() {
		this.sidenav.open().then();
	}

	onClose() {
		this.sidenav.close().then();
		this.stationService.clear();
	}
}
