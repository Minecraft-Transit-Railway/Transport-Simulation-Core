import {atan45, rotate} from "../data/utilities";

const tan225 = Math.tan(Math.PI / 8);

export function connectStations(
	canvasX1: number, canvasY1: number, canvasX2: number, canvasY2: number,
	direction1: 0 | 1 | 2 | 3, direction2: 0 | 1 | 2 | 3,
	offset1: number, offset2: number, colorOffset: number,
	canvasWidth: number, canvasHeight: number,
	oneWay: number, retry = true,
): [[number, number, boolean][], [number, number, number, number, number][]] {
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
	const [offset1X, offset1Y] = rotate(offset1 * getOffsetX(direction1), offset1 * getOffsetY(direction1), -direction1);
	const [offset2X, offset2Y] = rotate(offset2 * getOffsetX(direction2), offset2 * getOffsetY(direction2), -direction1);
	const newColorOffset = (direction1 < 2 ? 1 : -1) * colorOffset;
	const signX = Math.sign(x);
	const signY = Math.sign(y);
	const extraY = Math.max(0, Math.abs(y) - Math.abs(x)) * signY;
	const halfY = y / 2;
	const quadrant = x - offset1X + offset2X > 0 === y - offset1Y + offset2Y > 0 ? -1 : 1;
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
		if (retry) {
			return connectStations(canvasX2, canvasY2, canvasX1, canvasY1, direction2, direction1, offset2, offset1, colorOffset, canvasWidth, canvasHeight, oneWay, false);
		} else {
			return [[], []];
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

		const newPointsWithOffset: [number, number, boolean][] = [];
		newPointsWithOffset.push([newPoints[0][0], newPoints[0][1], true]);
		newPoints.forEach(([x, y]) => newPointsWithOffset.push([x, y, false]));
		newPointsWithOffset.push([newPoints[newPoints.length - 1][0], newPoints[newPoints.length - 1][1], true]);

		const oneWayPoints: [number, number, number, number, number][] = [];
		if (oneWay !== 0) {
			for (let i = 1; i < newPoints.length; i++) {
				const [point1X, point1Y] = newPoints[i - 1];
				const [point2X, point2Y] = newPoints[i];
				oneWayPoints.push([point1X, point1Y, point2X, point2Y, atan45(point2X - point1X, point2Y - point1Y) + (retry === (oneWay > 0) ? 0 : 4)]);
			}
		}

		return [newPointsWithOffset, oneWayPoints];
	}
}

export function connectWith45(points: [number, number][], x1: number, y1: number, x2: number, y2: number, start45: boolean) {
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
