import * as THREE from "./three.module.min.js";
import {atan45, rotate, trig45} from "../data/utilities";
import SETTINGS from "./settings";

const tan225 = Math.tan(Math.PI / 8);
const arrowSpacing = 80;

export function connectStations(
	positionAttribute: THREE.BufferAttribute, colorArray: Uint8Array,
	color: number, backgroundColor: number, textColor: number, blackAndWhite: boolean,
	index: number,
	canvasX1: number, canvasY1: number, canvasX2: number, canvasY2: number,
	direction1: 0 | 1 | 2 | 3, direction2: 0 | 1 | 2 | 3,
	offset1: number, offset2: number, colorOffset: number,
	lineZ: number, arrowZ: number, hollowZ: number,
	canvasWidth: number, canvasHeight: number,
	oneWay: number, hollow: boolean, retry = true
) {
	const getOffsetX = (direction: 0 | 1 | 2 | 3) => {
		switch (direction % 4) {
			case 1:
				return Math.SQRT1_2;
			case 2:
				return 0;
			case 3:
				return Math.SQRT1_2;
			default:
				return 1;
		}
	};

	const getOffsetY = (direction: 0 | 1 | 2 | 3) => {
		switch (direction % 4) {
			case 1:
				return -Math.SQRT1_2;
			case 2:
				return 1;
			case 3:
				return Math.SQRT1_2;
			default:
				return 0;
		}
	};

	const direction = (direction2 - direction1 + 4) % 4;
	const [x, y] = rotate(canvasX2 - canvasX1, canvasY2 - canvasY1, -direction1);
	const [offset1X] = rotate(offset1 * getOffsetX(direction1), offset1 * getOffsetY(direction1), -direction1);
	const [offset2X, offset2Y] = rotate(offset2 * getOffsetX(direction2), offset2 * getOffsetY(direction2), -direction1);
	const newColorOffset = (direction1 < 2 ? 1 : -1) * colorOffset;
	const signX = Math.sign(x);
	const signY = Math.sign(y);
	const extraY = Math.max(0, Math.abs(y) - Math.abs(x)) * signY;
	const halfY = y / 2;
	const quadrant = x > 0 === y > 0 ? -1 : 1;
	const horizontal = Math.abs(x) > Math.abs(y);
	const points: [number, number, boolean | undefined][] = [];

	if (direction === 0) {
		points.push([offset1X, 0, false]);
		if (horizontal) {
			const diagonalLength = Math.abs(y / 3);
			points.push([signX * diagonalLength + newColorOffset * tan225, halfY + quadrant * newColorOffset, true]);
			points.push([x - signX * diagonalLength + newColorOffset * tan225, halfY + quadrant * newColorOffset, true]);
		} else {
			points.push([offset1X, extraY / 2 - quadrant * offset1X + quadrant * newColorOffset * Math.SQRT2, true]);
		}
		points.push([x + offset2X, y, undefined]);
	} else if (direction === 2) {
		points.push([offset1X, 0, false]);
		points.push([signX * Math.abs((horizontal ? y : x) * 2 / 3) + quadrant * colorOffset * Math.SQRT2 - quadrant * offset2Y, y + offset2Y, true]);
		points.push([x, y + offset2Y, undefined]);
	} else if (quadrant > 0 && direction === 3 || quadrant < 0 && direction === 1) {
		points.push([offset1X, 0, false]);
		if (horizontal) {
			points.push([signX * Math.abs(y / 3) + newColorOffset * tan225, halfY + quadrant * newColorOffset, false]);
		}
		points.push([x + offset2X, y + offset2Y, undefined]);
	} else if (horizontal) {
		points.push([offset1X, 0, false]);
		points.push([x / 3 + newColorOffset * tan225, y + signY * Math.abs(x / 3) + quadrant * newColorOffset, false]);
		points.push([x + offset2X, y + offset2Y, undefined]);
	}

	if (points.length === 0) {
		console.assert(retry, "Line not drawn", quadrant, direction);
		if (retry) {
			return connectStations(positionAttribute, colorArray, color, backgroundColor, textColor, blackAndWhite, index, canvasX2, canvasY2, canvasX1, canvasY1, direction2, direction1, offset2, offset1, colorOffset, lineZ, arrowZ, hollowZ, canvasWidth, canvasHeight, oneWay, hollow, false);
		} else {
			return index;
		}
	} else {
		const newPoints: [number, number][] = [];

		for (let i = 1; i < points.length; i++) {
			const [point1X, point1Y, start45] = points[i - 1];
			const [point2X, point2Y] = points[i];
			const [x1, y1] = rotate(point1X, point1Y, direction1);
			const [x2, y2] = rotate(point2X, point2Y, direction1);
			connectWith45(newPoints, x1 + canvasX1, y1 + canvasY1, x2 + canvasX1, y2 + canvasY1, ((direction1 % 2) === 0) === start45);
		}

		for (let i = 1; i < newPoints.length; i++) {
			const [point1X, point1Y] = newPoints[i - 1];
			const [point2X, point2Y] = newPoints[i];

			if (inBounds1(point1X, point1Y, canvasWidth, canvasHeight) || inBounds1(point2X, point2Y, canvasWidth, canvasHeight) || inBounds2(point1X, point1Y, point2X, point2Y, canvasWidth, canvasHeight)) {
				index = drawLine(positionAttribute, colorArray, color, blackAndWhite, index, point1X, point1Y, point2X, point2Y, lineZ, 6);
				if (hollow) {
					index = drawLine(positionAttribute, colorArray, backgroundColor, blackAndWhite, index, point1X, point1Y, point2X, point2Y, hollowZ, 3);
				}

				if (oneWay !== 0) {
					const differenceX = point2X - point1X;
					const differenceY = point2Y - point1Y;
					const totalLength = Math.abs(differenceX) + Math.abs(differenceY);
					const newArrowSpacing = totalLength < arrowSpacing * SETTINGS.scale && totalLength > 24 ? totalLength : arrowSpacing * SETTINGS.scale;
					const arrowCount = Math.floor(totalLength / newArrowSpacing);
					const extraSpace = (totalLength - newArrowSpacing * arrowCount) / 2;
					const arrowAngle = -atan45(differenceY, differenceX) + (retry === (oneWay > 0) ? 2 : -2);
					for (let j = 0; j < arrowCount; j++) {
						const offsetFactor = (extraSpace + newArrowSpacing * (j + 0.5)) / totalLength;
						const arrowX = point1X + differenceX * offsetFactor;
						const arrowY = point1Y + differenceY * offsetFactor;
						if (inBounds1(arrowX, arrowY, canvasWidth, canvasHeight)) {
							index = drawArrow(positionAttribute, colorArray, hollow ? color : backgroundColor, blackAndWhite, index, arrowAngle, arrowX, arrowY, arrowZ);
						}
					}
				}
			}
		}

		return index;
	}
}

function connectWith45(points: [number, number][], x1: number, y1: number, x2: number, y2: number, start45: boolean) {
	const x = x2 - x1;
	const y = y2 - y1;
	const extraX = Math.max(0, Math.abs(x) - Math.abs(y)) * Math.sign(x);
	const extraY = Math.max(0, Math.abs(y) - Math.abs(x)) * Math.sign(y);
	const x3 = start45 ? x2 - extraX : x1 + extraX;
	const y3 = start45 ? y2 - extraY : y1 + extraY;
	points.push([x1, y1]);
	points.push([x3, y3]);
	points.push([x2, y2]);
}

export function drawLine(positionAttribute: THREE.BufferAttribute, colorArray: Uint8Array, color: number, blackAndWhite: boolean, index: number, x1: number, y1: number, x2: number, y2: number, z: number, width: number) {
	const angle = atan45(y2 - y1, x2 - x1);
	const [endOffsetX1, endOffsetY1] = trig45(angle + 2, width / 2 * SETTINGS.scale);
	const [endOffsetX2, endOffsetY2] = trig45(angle, tan225 * width / 2 * SETTINGS.scale);
	positionAttribute.setXYZ(index + 0, x1 + endOffsetX1 - endOffsetX2, -(y1 + endOffsetY1 - endOffsetY2), -z);
	positionAttribute.setXYZ(index + 1, x2 + endOffsetX1 + endOffsetX2, -(y2 + endOffsetY1 + endOffsetY2), -z);
	positionAttribute.setXYZ(index + 2, x2 - endOffsetX1 + endOffsetX2, -(y2 - endOffsetY1 + endOffsetY2), -z);
	positionAttribute.setXYZ(index + 3, x2 - endOffsetX1 + endOffsetX2, -(y2 - endOffsetY1 + endOffsetY2), -z);
	positionAttribute.setXYZ(index + 4, x1 - endOffsetX1 - endOffsetX2, -(y1 - endOffsetY1 - endOffsetY2), -z);
	positionAttribute.setXYZ(index + 5, x1 + endOffsetX1 - endOffsetX2, -(y1 + endOffsetY1 - endOffsetY2), -z);
	for (let i = 0; i < 6; i++) {
		setColorByIndex(colorArray, color, index + i, blackAndWhite);
	}
	return index + 6;
}

function drawArrow(positionAttribute: THREE.BufferAttribute, colorArray: Uint8Array, color: number, blackAndWhite: boolean, index: number, angle: number, x: number, y: number, z: number) {
	const [offset1X, offset1Y] = rotate(3 * SETTINGS.scale, 0, angle);
	const [offset2X, offset2Y] = rotate(0, 3 * SETTINGS.scale, angle);
	positionAttribute.setXYZ(index + 0, x - offset1X, -(y - offset1Y), -z);
	positionAttribute.setXYZ(index + 1, x + offset2X, -(y + offset2Y), -z);
	positionAttribute.setXYZ(index + 2, x + offset1X, -(y + offset1Y), -z);
	positionAttribute.setXYZ(index + 3, x, -y, -z);
	positionAttribute.setXYZ(index + 4, x + offset1X, -(y + offset1Y), -z);
	positionAttribute.setXYZ(index + 5, x + offset1X - offset2X, -(y + offset1Y - offset2Y), -z);
	positionAttribute.setXYZ(index + 6, x - offset1X - offset2X, -(y - offset1Y - offset2Y), -z);
	positionAttribute.setXYZ(index + 7, x - offset1X, -(y - offset1Y), -z);
	positionAttribute.setXYZ(index + 8, x, -y, -z);
	for (let i = 0; i < 9; i++) {
		setColorByIndex(colorArray, color, index + i, blackAndWhite);
	}
	return index + 9;
}

export function setColorByIndex(colorArray: Uint8Array, color: number, index: number, blackAndWhite: boolean) {
	const r = (color >> 16) & 0xFF;
	const g = (color >> 8) & 0xFF;
	const b = color & 0xFF;
	const colorComponents = [r, g, b];
	for (let i = 0; i < 3; i++) {
		colorArray[index * 3 + i] = blackAndWhite ? (r + g + b) / 3 : colorComponents[i];
	}
}

function inBounds1(x: number, y: number, canvasWidth: number, canvasHeight: number) {
	const check = (value: number, space: number) => Math.abs(value) <= space / 2;
	return check(x, canvasWidth) && check(y, canvasHeight);
}

function inBounds2(x1: number, y1: number, x2: number, y2: number, canvasWidth: number, canvasHeight: number) {
	const halfWidth = canvasWidth / 2;
	const halfHeight = canvasHeight / 2;
	const check = (check1a: number, check1b: number, check2a: number, check2b: number, border: number, space: number) => {
		const reverse = check1a > check2a;
		const check3a = reverse ? check2a : check1a;
		const check3b = reverse ? check2b : check1b;
		const check4a = reverse ? check1a : check2a;
		const check4b = reverse ? check1b : check2b;

		if (check3a < border && check4a > border) {
			return Math.abs(check3b + (border - check3a) * Math.sign(check4b - check3b)) <= space;
		} else {
			return false;
		}
	};
	return check(x1, y1, x2, y2, -halfWidth, halfHeight) || check(x1, y1, x2, y2, halfWidth, halfHeight) || check(y1, x1, y2, x2, -halfHeight, halfWidth) || check(y1, x1, y2, x2, halfHeight, halfWidth);
}
