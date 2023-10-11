export const ROUTE_TYPES = {
	train_normal: {icon: "directions_railway", text: "Train"},
	train_light_rail: {icon: "tram", text: "Light Rail"},
	train_high_speed: {icon: "train", text: "High Speed"},
	boat_normal: {icon: "sailing", text: "Ferry"},
	boat_light_rail: {icon: "directions_boat", text: "Cruise"},
	boat_high_speed: {icon: "snowmobile", text: "Fast Ferry"},
	cable_car_normal: {icon: "airline_seat_recline_extra", text: "Cable Car"},
	bus_normal: {icon: "directions_bus", text: "Bus"},
	bus_light_rail: {icon: "local_taxi", text: "Minibus"},
	bus_high_speed: {icon: "airport_shuttle", text: "Express Bus"},
	airplane_normal: {icon: "flight", text: "Plane"},
};

export function getColorStyle(style, parseString = false) {
	const color = getComputedStyle(document.body).getPropertyValue(`--${style}`).replace(/#/g, "");
	return parseString ? parseInt(color, 16) : color;
}

export function getCookie(name) {
	const splitCookies = document.cookie.split("; ").filter(cookie => cookie.startsWith(name + "="));
	if (splitCookies.length > 0 && splitCookies[0].includes("=")) {
		return decodeURIComponent(splitCookies[0].split("=")[1]);
	} else {
		return "";
	}
}

export function setCookie(name, value) {
	document.cookie = `${name}=${value}; expires=${new Date(2999, 11, 31).toUTCString()}; path=/`;
}

export function pushIfNotExists(array, element) {
	if (!array.includes(element)) {
		array.push(element);
	}
}

export function setIfUndefined(object, setValue) {
	if (object === undefined) {
		setValue();
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
