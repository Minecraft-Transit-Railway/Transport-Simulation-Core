import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "formatName",
	pure: true,
	standalone: true,
})
export class FormatNamePipe implements PipeTransform {

	transform(name: string): string {
		return name.replaceAll("|", " ");
	}
}
