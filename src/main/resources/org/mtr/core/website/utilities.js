export function getColorStyle(style) {
	return parseInt(getComputedStyle(document.body).getPropertyValue(`--${style}`).replace(/#/g, ""), 16);
}
