import {Component, DestroyRef, EventEmitter, inject, Input, NgZone, Output} from "@angular/core";
import {DrawerModule} from "primeng/drawer";
import {TooltipModule} from "primeng/tooltip";
import {ButtonModule} from "primeng/button";

function isVertical(): boolean {
	return window.innerWidth < window.innerHeight;
}

@Component({
	selector: "app-drawer",
	imports: [
		DrawerModule,
		ButtonModule,
		TooltipModule,
	],
	templateUrl: "./drawer.component.html",
	styleUrl: "./drawer.component.scss",
})
export class DrawerComponent {
	protected drawerVisible = false;
	protected drawerPosition: "bottom" | "right" = isVertical() ? "bottom" : "right";
	protected drawerStyle: Record<string, string> = isVertical()
		? {height: "48rem", maxHeight: "80%"}
		: {width: "24rem", maxWidth: "80%"};
	@Input({required: true}) title = "";
	@Output() closed = new EventEmitter<void>;

	private ngZone = inject(NgZone);
	private destroyRef = inject(DestroyRef);

	constructor() {
		const listener = () => this.ngZone.run(() => this.resize());
		window.addEventListener("resize", listener);
		this.destroyRef.onDestroy(() => window.removeEventListener("resize", listener));
	}

	open() {
		this.drawerVisible = true;
	}

	close() {
		this.drawerVisible = false;
	}

	private resize() {
		const vertical = isVertical();
		this.drawerPosition = vertical ? "bottom" : "right";
		this.drawerStyle = vertical ? {height: "48rem", maxHeight: "80%"} : {width: "24rem", maxWidth: "80%"};
	}
}
