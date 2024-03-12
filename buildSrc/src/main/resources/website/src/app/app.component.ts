import {Component} from "@angular/core";
import {MapComponent} from "./component/map/map.component";
import {PanelComponent} from "./component/panel/panel.component";
import {MatSidenav, MatSidenavContainer, MatSidenavContent} from "@angular/material/sidenav";
import {MatIcon} from "@angular/material/icon";
import {MatFabButton, MatIconButton} from "@angular/material/button";
import {MatToolbar} from "@angular/material/toolbar";
import {SearchComponent} from "./component/search/search.component";

@Component({
	selector: "app-root",
	standalone: true,
	imports: [
		MapComponent,
		PanelComponent,
		MatSidenavContainer,
		MatSidenav,
		MatSidenavContent,
		MatIcon,
		MatIconButton,
		MatFabButton,
		SearchComponent,
		MatToolbar,
	],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent {

	getTitle() {
		return document.title;
	}
}
