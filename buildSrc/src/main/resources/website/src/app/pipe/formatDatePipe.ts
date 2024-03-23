import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "formatTime",
	pure: true,
	standalone: true
})
export class FormatTimePipe implements PipeTransform {

	transform(timeDifference: number): string {
		if (timeDifference < 0) {
			return "";
		}

		const hour = Math.floor(timeDifference / 3600);
		const minute = Math.floor(timeDifference / 60) % 60;
		const second = timeDifference % 60;

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
