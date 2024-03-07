import {Component} from "@angular/core";
import {MatAutocomplete, MatAutocompleteTrigger, MatOption} from "@angular/material/autocomplete";
import {MatFormField, MatInput, MatLabel} from "@angular/material/input";

@Component({
	selector: "app-panel",
	standalone: true,
	imports: [
		MatAutocomplete,
		MatOption,
		MatInput,
		MatAutocompleteTrigger,
		MatLabel,
		MatFormField
	],
	templateUrl: "./panel.component.html",
	styleUrl: "./panel.component.css"
})
export class PanelComponent {

}
