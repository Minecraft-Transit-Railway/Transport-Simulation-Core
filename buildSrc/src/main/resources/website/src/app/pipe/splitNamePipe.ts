import {Pipe, PipeTransform} from "@angular/core";

@Pipe({
	name: "splitName",
	pure: true,
	standalone: true
})
export class SplitNamePipe implements PipeTransform {

	transform(name?: string): { text: string, isCjk: boolean }[] {
		return name == undefined ? [] : name.split("|").map(nameSplit => ({text: nameSplit, isCjk: SplitNamePipe.isCjk(nameSplit)}));
	}

	private static isCjk(text: string) {
		return text.match(/[\u3000-\u303F\u3040-\u309F\u30A0-\u30FF\uFF00-\uFF9F\u4E00-\u9FAF\u3400-\u4DBF]/) != null;
	}
}
