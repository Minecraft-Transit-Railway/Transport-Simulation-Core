import {provideHttpClient} from "@angular/common/http";
import {ApplicationConfig, inject, isDevMode, provideAppInitializer} from "@angular/core";
import {provideRouter} from "@angular/router";

import {provideIonicAngular} from "@ionic/angular/standalone";
import {provideTransloco} from "@jsverse/transloco";

import {routes} from "./app.routes";
import {SettingsService} from "./service/settings.service";
import {TranslocoHttpLoader} from "../transloco-loader";

export const appConfig: ApplicationConfig = {
	providers: [
		provideHttpClient(),
		provideIonicAngular(),
		provideRouter(routes),
		provideAppInitializer(() => inject(SettingsService).init()),
		provideTransloco({
			config: {
				availableLangs: ["en", "zh"],
				defaultLang: "en",
				reRenderOnLangChange: true,
				prodMode: !isDevMode(),
			},
			loader: TranslocoHttpLoader,
		}),
	],
};
