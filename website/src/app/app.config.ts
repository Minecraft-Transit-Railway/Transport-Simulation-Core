import {provideHttpClient} from "@angular/common/http";
import {ApplicationConfig, isDevMode} from "@angular/core";
import {provideTransloco} from "@jsverse/transloco";
import {providePrimeNG} from "primeng/config";

import {getCookie} from "./data/utilities";
import {FormatNamePipe} from "./pipe/format-name.pipe";
import {FormatTimePipe} from "./pipe/format-time.pipe";
import {SimplifyRoutesPipe} from "./pipe/simplify-routes.pipe";
import {SimplifyStationsPipe} from "./pipe/simplify-stations.pipe";
import {SplitNamePipe} from "./pipe/split-name.pipe";
import {myPreset} from "../theme-preset";
import {TranslocoHttpLoader} from "../transloco-loader";

export const appConfig: ApplicationConfig = {
	providers: [
		provideHttpClient(),
		providePrimeNG({
			theme: {
				preset: myPreset,
				options: {darkModeSelector: ".dark-theme"},
			},
		}),
		provideTransloco({
			config: {
				availableLangs: ["en", "zh"],
				defaultLang: getCookie("language") || "en",
				reRenderOnLangChange: true,
				prodMode: !isDevMode(),
			},
			loader: TranslocoHttpLoader,
		}),
		SimplifyStationsPipe,
		SimplifyRoutesPipe,
		SplitNamePipe,
		FormatNamePipe,
		FormatTimePipe,
	],
};
