import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "formatDate",
	pure: true,
	standalone: true,
})
export class FormatDatePipe implements PipeTransform {

	transform(millis: number): string {
		return new Date(millis).toLocaleTimeString();
	}
}
