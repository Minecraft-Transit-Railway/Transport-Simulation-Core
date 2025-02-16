import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import {AsyncPipe} from "@angular/common";
import {MatAutocomplete, MatAutocompleteTrigger, MatOption} from "@angular/material/autocomplete";
import {MatDivider} from "@angular/material/divider";
import {MatFormField, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {FormControl, ReactiveFormsModule} from "@angular/forms";
import {map, Observable} from "rxjs";
import {MapDataService} from "../../service/map-data.service";
import {SimplifyStationsPipe} from "../../pipe/simplifyStationsPipe";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {DataListEntryComponent} from "../data-list-entry/data-list-entry.component";
import {FormatColorPipe} from "../../pipe/formatColorPipe";


const maxResults = 50;

@Component({
	selector: "app-search",
	imports: [
		AsyncPipe,
		MatAutocomplete,
		MatDivider,
		MatOption,
		MatAutocompleteTrigger,
		MatFormField,
		MatInput,
		MatLabel,
		ReactiveFormsModule,
		FormatNamePipe,
		DataListEntryComponent,
		FormatColorPipe,
	],
	templateUrl: "./search.component.html",
	styleUrl: "./search.component.css",
})
export class SearchComponent implements OnInit {
	@Output() stationClicked = new EventEmitter<string>();
	@Output() routeClicked = new EventEmitter<string>();
	@Output() textCleared = new EventEmitter<void>();
	@Input({required: true}) label!: string;
	@Input({required: true}) includeRoutes!: boolean;
	@Input() setText!: EventEmitter<string>;
	searchBox = new FormControl("");
	searchedStations$ = new Observable<{ key: string, icons: string[], color: number, name: string, number: string }[]>();
	searchedRoutes$ = new Observable<{ key: string, icons: string[], color: number, name: string, number: string }[]>();
	hasStations = false;
	hasRoutes = false;

	constructor(private readonly dataService: MapDataService, private readonly simplifyStationsPipe: SimplifyStationsPipe, private readonly simplifyRoutesPipe: SimplifyRoutesPipe, private readonly formatNamePipe: FormatNamePipe) {
	}

	ngOnInit() {
		const filter = (getList: () => { key: string, icons: string[], color: number, name: string, number: string }[], setHasData: (value: boolean) => void): Observable<{ key: string, icons: string[], color: number, name: string, number: string }[]> => this.searchBox.valueChanges.pipe(map(value => {
			if (value == null || value === "") {
				return [];
			} else {
				const matches: { key: string, icons: string[], color: number, name: string, number: string, index: number }[] = [];
				getList().forEach(({key, icons, color, name, number}) => {
					const index = name.toLowerCase().indexOf(value.toLowerCase());
					if (index >= 0) {
						matches.push({key, icons, color, name, number, index});
					}
				});
				const result: { key: string, icons: string[], color: number, name: string, number: string }[] = matches.sort((match1, match2) => {
					const indexDifference = match1.index - match2.index;
					return indexDifference === 0 ? match1.name.localeCompare(match2.name) : indexDifference;
				});
				setHasData(result.length > 0);
				return result.slice(0, maxResults);
			}
		}));

		this.searchedStations$ = filter(() => this.simplifyStationsPipe.transform(this.dataService.stations), value => this.hasStations = value);
		this.searchedRoutes$ = filter(() => this.includeRoutes ? this.simplifyRoutesPipe.transform(this.dataService.routes) : [], value => this.hasRoutes = value);
		this.setText?.subscribe(text => this.searchBox.setValue(text));
	}

	onClickStation(station: { key: string, name: string }) {
		this.stationClicked.emit(station.key);
	}

	onClickRoute(route: { key: string, name: string }) {
		this.routeClicked.emit(route.key);
	}

	onTextChanged() {
		if (this.searchBox.getRawValue() === "") {
			this.textCleared.emit();
		}
	}

	getText() {
		return this.searchBox.getRawValue() ?? "";
	}
}
