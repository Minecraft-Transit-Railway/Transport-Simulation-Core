import {ChangeDetectionStrategy, Component} from "@angular/core";
import {SettingsComponent} from "../settings/settings.component";

@Component({
	selector: "app-home",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		SettingsComponent,
	],
	templateUrl: "./home.component.html",
	styleUrl: "./home.component.scss",
})
export class HomeComponent {

}
