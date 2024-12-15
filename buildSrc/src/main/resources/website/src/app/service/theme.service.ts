import {Injectable} from "@angular/core";
import {getCookie, setCookie} from "../data/utilities";
import {MapDataService} from "./map-data.service";

@Injectable({providedIn: "root"})
export class ThemeService {
	private darkTheme: boolean;

	constructor(private readonly mapDataService: MapDataService) {
		this.darkTheme = getCookie("dark-theme") === "true";
	}

	public setTheme(isDarkTheme: boolean) {
		this.darkTheme = isDarkTheme;
		setCookie("dark-theme", isDarkTheme.toString());
		setTimeout(() => this.mapDataService.drawMap.emit(), 0);
	}

	public isDarkTheme() {
		return this.darkTheme;
	}
}
