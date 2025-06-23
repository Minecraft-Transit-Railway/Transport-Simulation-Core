import {Component, EventEmitter, Input, Output} from "@angular/core";
import {FormGroup, FormsModule, ReactiveFormsModule} from "@angular/forms";
import {MapDataService} from "../../service/map-data.service";
import {SimplifyStationsPipe} from "../../pipe/simplifyStationsPipe";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {AutoCompleteCompleteEvent, AutoCompleteModule, AutoCompleteSelectEvent} from "primeng/autocomplete";
import {DividerModule} from "primeng/divider";
import {FloatLabelModule} from "primeng/floatlabel";
import {InputTextModule} from "primeng/inputtext";
import {SelectItemGroup} from "primeng/api";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatColorPipe} from "../../pipe/formatColorPipe";
import {SearchData} from "../../entity/searchData";


const maxResults = 50;

@Component({
	selector: "app-search",
	imports: [
		FloatLabelModule,
		InputTextModule,
		AutoCompleteModule,
		DividerModule,
		FormsModule,
		FormatNamePipe,
		FormatColorPipe,
		DataListEntryComponent,
		ReactiveFormsModule,
	],
	templateUrl: "./search.component.html",
	styleUrl: "./search.component.css",
})
export class SearchComponent {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() textCleared = new EventEmitter<void>();
	@Input({required: true}) label = "";
	@Input({required: true}) parentFormGroup!: FormGroup;
	@Input({required: true}) childFormControlName = "";
	@Input({required: true}) includeRoutes = true;
	@Input() setText!: EventEmitter<string>;

	protected data: SelectItemGroup[] = [];

	constructor(private readonly dataService: MapDataService, private readonly simplifyStationsPipe: SimplifyStationsPipe, private readonly simplifyRoutesPipe: SimplifyRoutesPipe) {
	}

	onTextChanged(event: AutoCompleteCompleteEvent) {
		this.data = [];

		if (event.query === "") {
			this.textCleared.emit();
		} else {
			const filter = (list: SearchData[]): { value: { key: string, icons: string[], color: number, name: string, number: string, isStation: boolean } }[] => {
				const matches: { value: SearchData, index: number }[] = [];
				list.forEach(({key, icons, color, name, number, isStation}) => {
					const index = name.toLowerCase().indexOf(event.query.toLowerCase());
					if (index >= 0) {
						matches.push({value: {key, icons, color, name, number, isStation}, index});
					}
				});
				const result: { value: SearchData }[] = matches.sort((match1, match2) => {
					const indexDifference = match1.index - match2.index;
					return indexDifference === 0 ? match1.value.name.localeCompare(match2.value.name) : indexDifference;
				});
				return result.slice(0, maxResults);
			};

			const searchedStations = filter(this.simplifyStationsPipe.transform(this.dataService.stations));
			const searchedRoutes = filter(this.includeRoutes ? this.simplifyRoutesPipe.transform(this.dataService.routes) : []);

			if (searchedStations.length > 0) {
				this.data.push({
					label: "Stations",
					items: searchedStations,
				});
			}

			if (searchedRoutes.length > 0) {
				this.data.push({
					label: "Routes",
					items: searchedRoutes,
				});
			}
		}
	}

	onSelect(event: AutoCompleteSelectEvent) {
		if (event?.value?.value) {
			if (event.value.value.isStation) {
				this.stationClicked.emit(event.value.value.key);
			} else {
				this.routeClicked.emit(event.value.value.key);
			}
		}
	}

	getName(entry: { value?: { name?: string } }) {
		const name = entry?.value?.name;
		return name ? name.replaceAll("|", " ") : "";
	}

	getText() {
		return "";
	}
}
