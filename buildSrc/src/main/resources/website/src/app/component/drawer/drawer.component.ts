import {Component, EventEmitter, Input, Output} from "@angular/core";
import {DrawerModule} from "primeng/drawer";
import {TooltipModule} from "primeng/tooltip";
import {ButtonModule} from "primeng/button";

@Component({
	selector: "app-drawer",
	imports: [
		DrawerModule,
		ButtonModule,
		TooltipModule,
	],
	templateUrl: "./drawer.component.html",
	styleUrl: "./drawer.component.css",
})
export class DrawerComponent {
	protected drawerVisible = false;
	@Input({required: true}) title = "";
	@Output() closed = new EventEmitter<void>;

	open() {
		this.drawerVisible = true;
	}

	close() {
		this.drawerVisible = false;
	}
}
