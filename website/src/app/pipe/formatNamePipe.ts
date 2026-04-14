import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "formatName",
	pure: true,
})
export class FormatNamePipe implements PipeTransform {

	transform(name: string): string {
		return name.replaceAll("|", " ");
	}
}
