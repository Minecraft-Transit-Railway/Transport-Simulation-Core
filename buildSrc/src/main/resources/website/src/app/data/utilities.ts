export function getColorStyle(style: string, parseString = false) {
	const color = getComputedStyle(document.body).getPropertyValue(`--${style}`).replace(/#/g, "");
	return parseString ? parseInt(color, 16) : color;
}

export function getCookie(name: string) {
	const splitCookies = document.cookie.split("; ").filter(cookie => cookie.startsWith(name + "="));
	if (splitCookies.length > 0 && splitCookies[0].includes("=")) {
		return decodeURIComponent(splitCookies[0].split("=")[1]);
	} else {
		return "";
	}
}

export function setCookie(name: string, value: string) {
	document.cookie = `${name}=${value}; expires=${new Date(2999, 11, 31).toUTCString()}; path=/`;
}

export function pushIfNotExists(array: string[], element: string) {
	if (!array.includes(element)) {
		array.push(element);
	}
}

export function setIfUndefined<T>(map: { [key: string]: T }, key: string, createInstance: () => T) {
	if (!(key in map)) {
		map[key] = createInstance();
	}
}

export function arrayAverage(array: number[]) {
	return array.reduce((previousTotal, currentValue) => previousTotal + currentValue, 0) / array.length;
}

export function isCJK(text: string) {
	return text.match(/[\u3000-\u303F\u3040-\u309F\u30A0-\u30FF\uFF00-\uFF9F\u4E00-\u9FAF\u3400-\u4DBF]/);
}

export function atan45(y: number, x: number) {
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

export function trig45(angle: number, scale = 1) {
	switch ((angle + 8) % 8) {
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
		default:
			return [scale, 0];
	}
}

export function rotate(x: number, z: number, angle: number) {
	const [cos, sin] = trig45(angle);
	return [x * cos + z * sin, z * cos - x * sin];
}
