import {bootstrapApplication} from "@angular/platform-browser";
import {AppComponent} from "./app/app.component";
import "reflect-metadata";

bootstrapApplication(AppComponent, {providers: []}).catch(err => console.error(err));
