import {bootstrapApplication} from "@angular/platform-browser";
import {AppComponent} from "./app/app.component";
import {registerIcons} from "./app/utility/icons";
import {appConfig} from "./app/app.config";

registerIcons();

bootstrapApplication(AppComponent, appConfig).catch(error => console.error(error));
