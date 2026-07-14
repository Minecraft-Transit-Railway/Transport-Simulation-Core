import {ChangeDetectionStrategy, Component, inject} from "@angular/core";
import {ActivatedRoute} from "@angular/router";

@Component({
	selector: "app-station",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [],
	templateUrl: "./station.component.html",
	styleUrl: "./station.component.scss",
})
export class StationComponent {
	readonly stationId = inject(ActivatedRoute).snapshot.paramMap.get("id");
}
