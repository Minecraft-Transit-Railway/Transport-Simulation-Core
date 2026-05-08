import {bootstrapApplication} from "@angular/platform-browser";
import {AppComponent} from "./app/app.component";
import {provideHttpClient} from "@angular/common/http";
import {SimplifyRoutesPipe} from "./app/pipe/simplify-routes.pipe";
import {SimplifyStationsPipe} from "./app/pipe/simplify-stations.pipe";
import {FormatNamePipe} from "./app/pipe/format-name.pipe";
import {FormatTimePipe} from "./app/pipe/format-time.pipe";
import {SplitNamePipe} from "./app/pipe/split-name.pipe";
import {isDevMode} from "@angular/core";
import {providePrimeNG} from "primeng/config";
import {myPreset} from "./theme-preset";
import {provideTransloco} from "@jsverse/transloco";
import {TranslocoHttpLoader} from "./transloco-loader";
import {registerIcons} from "./app/utility/icons";
import {getCookie} from "./app/data/utilities";

registerIcons();

bootstrapApplication(AppComponent, {
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
}).catch(error => console.error(error));
