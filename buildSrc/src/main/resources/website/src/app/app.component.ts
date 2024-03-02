import {Component} from "@angular/core";
import {MapComponent} from "./map/map.component";

@Component({
	selector: "app-root",
	standalone: true,
	imports: [MapComponent],
	templateUrl: "./app.component.html",
	styleUrls: ["./app.component.css"],
})
export class AppComponent {
	title = "default";
}
