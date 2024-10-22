import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import {AsyncPipe} from "@angular/common";
import {MatAutocomplete, MatAutocompleteTrigger, MatOption} from "@angular/material/autocomplete";
import {MatDivider} from "@angular/material/divider";
import {MatFormField, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {FormControl, ReactiveFormsModule} from "@angular/forms";
import {map, Observable} from "rxjs";
import {DataService} from "../../service/data.service";
import {SimplifyStationsPipe} from "../../pipe/simplifyStationsPipe";
import {SimplifyRoutesPipe} from "../../pipe/simplifyRoutesPipe";
import {FormatNamePipe} from "../../pipe/formatNamePipe";
import {StationService} from "../../service/station.service";

@Component({
	selector: "app-search",
	standalone: true,
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
	],
	templateUrl: "./search.component.html",
	styleUrl: "./search.component.css",
})
export class SearchComponent implements OnInit {
	@Output() onClickStation = new EventEmitter<string>();
	@Output() onClickRoute = new EventEmitter<string>();
	@Input() label!: string;
	@Input() includeRoutes!: boolean;
	searchBox = new FormControl("");
	searchedStations$ = new Observable<{ id: string, color: string, name: string }[]>();
	searchedRoutes$ = new Observable<{ id: string, color: string, name: string }[]>();
	hasStations = false;
	hasRoutes = false;

	constructor(private readonly dataService: DataService, private readonly stationService: StationService, private readonly simplifyStationsPipe: SimplifyStationsPipe, private readonly simplifyRoutesPipe: SimplifyRoutesPipe) {
	}

	ngOnInit() {
		const filter = (getList: () => { id: string, color: string, name: string }[], setHasData: (value: boolean) => void): Observable<{ id: string, color: string, name: string }[]> => this.searchBox.valueChanges.pipe(map(value => {
			if (value == null || value === "") {
				return [];
			} else {
				const matches: { id: string, color: string, name: string, index: number }[] = [];
				getList().forEach(({id, color, name}) => {
					const index = name.toLowerCase().indexOf(value.toLowerCase());
					if (index >= 0) {
						matches.push({id, color, name, index});
					}
				});
				const result: { id: string, color: string, name: string }[] = matches.sort((match1, match2) => {
					const indexDifference = match1.index - match2.index;
					return indexDifference === 0 ? match1.name.localeCompare(match2.name) : indexDifference;
				});
				setHasData(result.length > 0);
				return result;
			}
		}));

		this.searchedStations$ = filter(() => this.simplifyStationsPipe.transform(this.dataService.getAllStations()), value => this.hasStations = value);
		this.searchedRoutes$ = filter(() => this.includeRoutes ? this.simplifyRoutesPipe.transform(this.dataService.getAllRoutes()) : [], value => this.hasRoutes = value);
	}
}
