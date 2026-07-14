import {ChangeDetectionStrategy, Component, computed, inject} from "@angular/core";
import {NavigationEnd, Router} from "@angular/router";
import {toSignal} from "@angular/core/rxjs-interop";
import {filter, map} from "rxjs";

import {IonApp, IonBackButton, IonButton, IonButtons, IonContent, IonFab, IonFabButton, IonHeader, IonIcon, IonMenu, IonRouterOutlet, IonSplitPane, IonToolbar} from "@ionic/angular/standalone";
import {addIcons} from "ionicons";
import {close, menu} from "ionicons/icons";

import {MapComponent} from "./component/map/map.component";

@Component({
	selector: "app-root",
	imports: [
		IonApp,
		IonBackButton,
		IonButton,
		IonButtons,
		IonContent,
		IonFab,
		IonFabButton,
		IonHeader,
		IonIcon,
		IonMenu,
		IonRouterOutlet,
		IonSplitPane,
		IonToolbar,
		MapComponent,
	],
	templateUrl: "./app.component.html",
	styleUrl: "./app.component.scss",
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
	private readonly router = inject(Router);
	private readonly url = toSignal(this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd), map(() => this.router.url)), {initialValue: this.router.url});

	protected readonly showSplitPane = computed(() => this.url() !== "/");
	protected readonly canGoBack = computed(() => this.url().startsWith("/station/"));

	protected openPanel() {
		this.router.navigate(["/home"]);
	}

	protected closePanel() {
		this.router.navigate(["/"]);
	}

	constructor() {
		addIcons({close, menu});
	}
}
