import {Routes} from "@angular/router";

export const routes: Routes = [
	{path: "", loadComponent: () => import("./component/home/home.component").then(module => module.HomeComponent)},
	{path: "home", loadComponent: () => import("./component/home/home.component").then(module => module.HomeComponent)},
	{path: "station/:id", loadComponent: () => import("./component/station/station.component").then(module => module.StationComponent)},
];
