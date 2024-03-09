import "reflect-metadata";
import {bootstrapApplication} from "@angular/platform-browser";
import {AppComponent} from "./app/app.component";
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {provideHttpClient} from "@angular/common/http";
import {SimplifyRoutesPipe} from "./app/pipe/simplifyRoutesPipe";
import {SimplifyStationsPipe} from "./app/pipe/simplifyStationsPipe";

bootstrapApplication(AppComponent, {providers: [provideAnimationsAsync(), provideHttpClient(), SimplifyStationsPipe, SimplifyRoutesPipe]}).catch(err => console.error(err));
