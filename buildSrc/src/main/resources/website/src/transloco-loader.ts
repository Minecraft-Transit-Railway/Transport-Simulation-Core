import {Injectable} from "@angular/core";
import {TranslocoLoader} from "@jsverse/transloco";
import {HttpClient} from "@angular/common/http";

@Injectable({providedIn: "root"})
export class TranslocoHttpLoader implements TranslocoLoader {

	constructor(private readonly httpClient: HttpClient) {
	}

	getTranslation(lang: string) {
		return this.httpClient.get(`./assets/i18n/${lang}.json`);
	}
}
