export function getColorStyle(style) {
	return parseInt(getComputedStyle(document.body).getPropertyValue(`--${style}`).replace(/#/g, ""), 16);
}

export function pushIfNotExists(array, element) {
	if (!array.includes(element)) {
		array.push(element);
	}
}

export function isCJK(text) {
	return text.match(/[\u3000-\u303F\u3040-\u309F\u30A0-\u30FF\uFF00-\uFF9F\u4E00-\u9FAF\u3400-\u4DBF]/);
}

export function atan45(y, x) {
	const absX = Math.abs(x);
	const absY = Math.abs(y);
	if (absX === 0 && absY === 0) {
		return 0;
	} else if (absY < absX / 2) {
		return x > 0 ? 0 : 4;
	} else if (absX < absY / 2) {
		return y > 0 ? 2 : -2;
	} else if (x > 0) {
		return y > 0 ? 1 : -1;
	} else {
		return y > 0 ? 3 : -3;
	}
}

export function trig45(angle, scale = 1) {
	switch ((angle + 8) % 8) {
		case 0:
			return [scale, 0];
		case 1:
			return [Math.SQRT1_2 * scale, Math.SQRT1_2 * scale];
		case 2:
			return [0, scale];
		case 3:
			return [-Math.SQRT1_2 * scale, Math.SQRT1_2 * scale];
		case 4:
			return [-scale, 0];
		case 5:
			return [-Math.SQRT1_2 * scale, -Math.SQRT1_2 * scale];
		case 6:
			return [0, -scale];
		case 7:
			return [Math.SQRT1_2 * scale, -Math.SQRT1_2 * scale];
	}
}

export function rotate(x, z, angle) {
	const [cos, sin] = trig45(angle);
	return [x * cos + z * sin, z * cos - x * sin];
}
