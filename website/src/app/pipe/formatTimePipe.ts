import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "formatTime",
	pure: true,
	standalone: true,
})
export class FormatTimePipe implements PipeTransform {

	transform(time: number, defaultValue: string): string {
		if (time < 0) {
			return defaultValue;
		}

		const hour = Math.floor(time / 3600);
		const minute = Math.floor(time / 60) % 60;
		const second = time % 60;

		if (hour > 0) {
			return `${hour}:${FormatTimePipe.formatNumber(minute)}:${FormatTimePipe.formatNumber(second)}`;
		} else {
			return `${minute}:${FormatTimePipe.formatNumber(second)}`;
		}
	}

	private static formatNumber(value: number) {
		return value.toString().padStart(2, "0");
	}
}
