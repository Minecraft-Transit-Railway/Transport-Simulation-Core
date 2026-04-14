import {ChangeDetectionStrategy, Component, DestroyRef, inject, input, NgZone, output, signal} from "@angular/core";
import {DrawerModule} from "primeng/drawer";
import {TooltipModule} from "primeng/tooltip";
import {ButtonModule} from "primeng/button";

function isVertical(): boolean {
	return window.innerWidth < window.innerHeight;
}

@Component({
	selector: "app-drawer",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		DrawerModule,
		ButtonModule,
		TooltipModule,
	],
	templateUrl: "./drawer.component.html",
	styleUrl: "./drawer.component.scss",
})
export class DrawerComponent {
	protected readonly drawerVisible = signal(false);
	protected readonly drawerPosition = signal<"bottom" | "right">(isVertical() ? "bottom" : "right");
	protected readonly drawerStyle = signal<Record<string, string>>(isVertical()
		? {height: "48rem", maxHeight: "80%"}
		: {width: "24rem", maxWidth: "80%"});
	readonly title = input.required<string>();
	readonly closed = output<void>();

	private ngZone = inject(NgZone);
	private destroyRef = inject(DestroyRef);

	constructor() {
		const listener = () => this.ngZone.run(() => this.resize());
		window.addEventListener("resize", listener);
		this.destroyRef.onDestroy(() => window.removeEventListener("resize", listener));
	}

	open() {
		this.drawerVisible.set(true);
	}

	close() {
		this.drawerVisible.set(false);
	}

	private resize() {
		const vertical = isVertical();
		this.drawerPosition.set(vertical ? "bottom" : "right");
		this.drawerStyle.set(vertical ? {height: "48rem", maxHeight: "80%"} : {width: "24rem", maxWidth: "80%"});
	}
}
