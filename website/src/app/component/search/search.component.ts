import {Component, EventEmitter, inject, Input, Output} from "@angular/core";
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
import {ClientsService} from "../../service/clients.service";


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
	styleUrl: "./search.component.scss",
})
export class SearchComponent {
	private readonly dataService = inject(MapDataService);
	private readonly clientsService = inject(ClientsService);
	private readonly simplifyStationsPipe = inject(SimplifyStationsPipe);
	private readonly simplifyRoutesPipe = inject(SimplifyRoutesPipe);

	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() clientClicked = new EventEmitter<string>();
	@Output() textCleared = new EventEmitter<void>();
	@Input({required: true}) label = "";
	@Input({required: true}) parentFormGroup!: FormGroup;
	@Input({required: true}) childFormControlName = "";
	@Input({required: true}) includeRoutes = true;
	@Input() setText!: EventEmitter<string>;

	protected data: SelectItemGroup[] = [];

	onTextChanged(event: AutoCompleteCompleteEvent) {
		this.data = [];

		if (event.query === "") {
			this.textCleared.emit();
		} else {
			const filter = (list: SearchData[]): { value: { key: string, icons: string[], color?: number, name: string, number: string, type: "station" | "route" | "client" } }[] => {
				const matches: { value: SearchData, index: number }[] = [];
				list.forEach(({key, icons, color, name, number, type}) => {
					const index = name.toLowerCase().indexOf(event.query.toLowerCase());
					if (index >= 0) {
						matches.push({value: {key, icons, color, name, number, type}, index});
					}
				});
				const result: { value: SearchData }[] = matches.sort((match1, match2) => {
					const indexDifference = match1.index - match2.index;
					return indexDifference === 0 ? match1.value.name.localeCompare(match2.value.name) : indexDifference;
				});
				return result.slice(0, maxResults);
			};

			const searchedStations = filter(this.simplifyStationsPipe.transform(this.dataService.stations()));
			const searchedRoutes = filter(this.includeRoutes ? this.simplifyRoutesPipe.transform(this.dataService.routes()) : []);
			const searchedClients = filter(this.clientsService.allClients().map(client => ({key: client.id, icons: [`https://mc-heads.net/avatar/${client.id}`], name: client.name, number: "", type: "client"})));

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

			if (searchedClients.length > 0) {
				this.data.push({
					label: "Players",
					items: searchedClients,
				});
			}
		}
	}

	onSelect(event: AutoCompleteSelectEvent) {
		if (event?.value?.value) {
			switch (event.value.value.type) {
				case "station":
					this.stationClicked.emit(event.value.value.key);
					break;
				case "route":
					this.routeClicked.emit(event.value.value.key);
					break;
				case "client":
					this.clientClicked.emit(event.value.value.key);
					break;
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
