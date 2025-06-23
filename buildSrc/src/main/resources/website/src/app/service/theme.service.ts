import {Injectable} from "@angular/core";
import {getCookie, setCookie} from "../data/utilities";
import {MapDataService} from "./map-data.service";

@Injectable({providedIn: "root"})
export class ThemeService {
	private darkTheme: boolean;

	constructor(private readonly mapDataService: MapDataService) {
		this.darkTheme = getCookie("dark_theme") === "true";
		this.setElementTag();
	}

	public setTheme(isDarkTheme: boolean) {
		this.darkTheme = isDarkTheme;
		this.setElementTag();
		setTimeout(() => this.mapDataService.drawMap.emit(), 0);
	}

	public isDarkTheme() {
		return this.darkTheme;
	}

	private setElementTag() {
		setCookie("dark_theme", this.darkTheme.toString());

		const element = document.querySelector("html");
		if (element) {
			element.classList.add(this.darkTheme ? "dark-theme" : "light-theme");
			element.classList.remove(this.darkTheme ? "light-theme" : "dark-theme");
		}
	}
}
