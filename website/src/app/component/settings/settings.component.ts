import {ChangeDetectionStrategy, Component, inject} from "@angular/core";

import {IonItem, IonLabel, IonList, IonSelect, IonSelectOption, IonToggle} from "@ionic/angular/standalone";
import {TranslocoPipe, TranslocoService} from "@jsverse/transloco";

import {LANGUAGE_MAPPING, SettingsService} from "../../service/settings.service";

@Component({
	selector: "app-settings",
	imports: [
		IonItem,
		IonLabel,
		IonList,
		IonSelect,
		IonSelectOption,
		IonToggle,
		TranslocoPipe,
	],
	templateUrl: "./settings.component.html",
	styleUrl: "./settings.component.scss",
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
	private readonly settingsService = inject(SettingsService);
	private readonly translocoService = inject(TranslocoService);

	protected readonly darkMode = this.settingsService.darkMode;
	protected readonly language = this.settingsService.language;
	protected readonly languages = this.translocoService.getAvailableLangs().map(availableLanguage => availableLanguage.toString()).map(availableLanguage => ({key: availableLanguage, value: LANGUAGE_MAPPING[availableLanguage]}));

	protected toggleDarkMode(): void {
		this.settingsService.darkMode.set(!this.settingsService.darkMode());
	}

	protected setLanguage(event: CustomEvent): void {
		this.settingsService.language.set(event.detail.value);
	}
}
