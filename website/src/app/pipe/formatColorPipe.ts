import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "formatColor",
	pure: true,
	standalone: true,
})
export class FormatColorPipe implements PipeTransform {

	transform(color: number): string {
		return `#${color.toString(16).padStart(6, "0")}`;
	}
}
