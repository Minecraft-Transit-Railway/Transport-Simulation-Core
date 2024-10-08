export class Mouse {

	private readonly animateSteps: number = 50;
	private zoom: number = 1;
	private centerX: number = 0;
	private centerY: number = 0;
	private animateTarget: [number, number, number, number] | undefined;
	private isMouseDown: boolean = false;
	private readonly enabled: boolean = true;
	private firstDraw: boolean = true;
	private touchPoints: number[][] = [];
	private readonly mouseCallback;

	constructor(
		private canvasElement: HTMLCanvasElement,
		wrapperElement: HTMLDivElement,
		mouseCallback: (zoom: number, centerX: number, centerY: number) => void,
		resizeCallback: (zoom: number, centerX: number, centerY: number) => void,
	) {
		wrapperElement.addEventListener("wheel", (event: WheelEvent) => {
			const {clientX, clientY, deltaY} = event;
			this.zoomCanvas(clientX, clientY, deltaY);
			event.preventDefault();
		}, {passive: false});

		wrapperElement.addEventListener("mousemove", (event: MouseEvent) => {
			if (this.isMouseDown) {
				const {movementX, movementY} = event;
				this.moveCanvas(movementX, movementY);
			}
			event.preventDefault();
		}, {passive: false});

		wrapperElement.addEventListener("touchmove", (event: TouchEvent) => {
			const touchCount = this.touchPoints.length;
			const {touches} = event;
			if (touches.length === touchCount) {
				let movementX = 0;
				let movementY = 0;
				for (let i = 0; i < touchCount; i++) {
					movementX += touches.item(i)!.clientX - this.touchPoints[i][0];
					movementY += touches.item(i)!.clientY - this.touchPoints[i][1];
				}
				this.moveCanvas(movementX / touchCount, movementY / touchCount);
				if (touchCount >= 2) {
					const pinchX = Math.abs(this.touchPoints[1][0] - this.touchPoints[0][0]) - Math.abs(touches.item(1)!.clientX - touches.item(0)!.clientX);
					const pinchY = Math.abs(this.touchPoints[1][1] - this.touchPoints[0][1]) - Math.abs(touches.item(1)!.clientY - touches.item(0)!.clientY);
					this.zoomCanvas((this.touchPoints[0][0] + this.touchPoints[1][0]) / 2, (this.touchPoints[0][1] + this.touchPoints[1][1]) / 2, (pinchX + pinchY) * 3);
				}
			}
			this.setTouchPoints(event);
			event.preventDefault();
		}, {passive: false});

		wrapperElement.addEventListener("mousedown", () => {
			this.isMouseDown = this.enabled;
			this.animateTarget = undefined;
		});

		wrapperElement.addEventListener("mouseup", () => {
			this.isMouseDown = false;
			this.animateTarget = undefined;
		});

		wrapperElement.addEventListener("mouseleave", () => {
			this.isMouseDown = false;
			this.animateTarget = undefined;
		});

		wrapperElement.addEventListener("touchstart", event => {
			this.setTouchPoints(event);
			this.animateTarget = undefined;
		});

		wrapperElement.addEventListener("touchend", () => {
			this.touchPoints = [];
			this.animateTarget = undefined;
		});

		this.mouseCallback = () => mouseCallback(this.zoom, this.centerX, this.centerY);
		window.onresize = () => resizeCallback(this.zoom, this.centerX, this.centerY);
	}

	private zoomCanvas(clientX: number, clientY: number, deltaY: number) {
		if (this.enabled) {
			const zoomFactor = 0.999 ** deltaY;
			const x = clientX - this.canvasElement.clientWidth / 2 - this.centerX;
			const y = clientY - this.canvasElement.clientHeight / 2 - this.centerY;
			this.zoom *= zoomFactor;
			this.centerX += x - x * zoomFactor;
			this.centerY += y - y * zoomFactor;
			this.mouseCallback();
		}
		this.animateTarget = undefined;
	}

	private moveCanvas(movementX: number, movementY: number) {
		if (this.enabled) {
			this.centerX += movementX;
			this.centerY += movementY;
			this.mouseCallback();
		}
		this.animateTarget = undefined;
	}

	private setTouchPoints(event: TouchEvent) {
		this.touchPoints = [];
		for (let i = 0; i < event.touches.length; i++) {
			const touchList = event.touches.item(i)!;
			this.touchPoints.push([touchList.clientX, touchList.clientY]);
		}
	}

	public setCenterOnFirstDraw(x: number, y: number) {
		if (this.firstDraw) {
			this.centerX = x;
			this.centerY = y;
			this.zoom = 1;
		}
		this.firstDraw = false;
	}

	public animateCenter(x: number, y: number) {
		this.animateTarget = [x - this.centerX, y - this.centerY, 1 - this.zoom, 0];
		const animate = () => {
			if (this.animateTarget !== undefined) {
				const [changeX, changeY, changeZoom, progress] = this.animateTarget;
				const scaledProgress = Math.sin(progress) * Math.PI / this.animateSteps / 2;
				const incrementX = changeX * scaledProgress;
				const incrementY = changeY * scaledProgress;
				const incrementZoom = changeZoom * scaledProgress;
				if (progress >= Math.PI) {
					this.centerX = x;
					this.centerY = y;
					this.zoom = 1;
					this.animateTarget = undefined;
				} else {
					this.centerX += incrementX;
					this.centerY += incrementY;
					this.zoom += incrementZoom;
					this.animateTarget[3] += Math.PI / this.animateSteps;
					requestAnimationFrame(animate);
				}
				this.mouseCallback();
			}
		};
		animate();
	}

	public getCurrentWindowValues() {
		return [this.zoom, this.centerX, this.centerY];
	}
}
