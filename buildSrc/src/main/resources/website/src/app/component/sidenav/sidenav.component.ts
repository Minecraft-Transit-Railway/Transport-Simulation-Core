import {Component, EventEmitter, Output, ViewChild} from "@angular/core";
import {MatButtonModule} from "@angular/material/button";
import {MatIconModule} from "@angular/material/icon";
import {MatSidenav, MatSidenavModule} from "@angular/material/sidenav";
import {MatToolbarModule} from "@angular/material/toolbar";
import {MatTooltipModule} from "@angular/material/tooltip";

@Component({
	selector: "app-sidenav",
	imports: [
		MatIconModule,
		MatButtonModule,
		MatSidenavModule,
		MatToolbarModule,
		MatTooltipModule,
	],
	templateUrl: "./sidenav.component.html",
	styleUrl: "./sidenav.component.css",
})
export class SidenavComponent {
	@ViewChild(MatSidenav) private readonly sidenav!: MatSidenav;
	@Output() closed = new EventEmitter<void>;

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
