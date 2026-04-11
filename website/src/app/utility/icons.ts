import {addIcon} from "iconify-icon";

// Static UI icons
import menu from "@iconify-icons/mdi/menu";
import location from "@iconify-icons/mdi/my-location";
import copy from "@iconify-icons/mdi/content-copy";
import focus from "@iconify-icons/mdi/image-filter-center-focus";
import fareZone from "@iconify-icons/mdi/tag-multiple";
import directions from "@iconify-icons/mdi/directions";
import schedule from "@iconify-icons/mdi/schedule";
import depot from "@iconify-icons/mdi/shed";
import duration from "@iconify-icons/mdi/hourglass-empty";
import swap from "@iconify-icons/mdi/swap-vertical";
import refresh from "@iconify-icons/mdi/refresh";
import noDirections from "@iconify-icons/mdi/sign-direction-remove";
import interchange from "@iconify-icons/mdi/transit-connection-horizontal";
import transfer from "@iconify-icons/mdi/transit-transfer";
import check from "@iconify-icons/mdi/check";

// Visibility / interchange toggle icons
import hidden from "@iconify-icons/mdi/hide";
import solid from "@iconify-icons/fluent/line-horizontal-1-24-regular";
import hollow from "@iconify-icons/fluent/re-order-24-regular";
import dashed from "@iconify-icons/fluent/line-horizontal-1-dashes-24-regular";

// Route type icons (referenced dynamically via routeType.ts)
import train from "@iconify-icons/mdi/subway-variant";
import lightRail from "@iconify-icons/mdi/tram";
import highSpeedRail from "@iconify-icons/mdi/train";
import ferry from "@iconify-icons/mdi/sail-boat";
import cruise from "@iconify-icons/mdi/boat";
import fastFerry from "@iconify-icons/mdi/sail-boat-sink";
import cableCar from "@iconify-icons/mdi/cable-car";
import bus from "@iconify-icons/mdi/bus-double-decker";
import minibus from "@iconify-icons/mdi/bus";
import expressBus from "@iconify-icons/mdi/bus-side";
import plane from "@iconify-icons/mdi/airplane";

// Circular state icons
import clockwise from "@iconify-icons/mdi/rotate-clockwise";
import anticlockwise from "@iconify-icons/mdi/rotate-counter-clockwise";

// Misc icons
import walk from "@iconify-icons/mdi/directions-walk";
import arrowDown from "@iconify-icons/mdi/arrow-down";

/**
 * Pre-registers all icons used in the app so they are bundled and available offline without CDN requests.
 */
export function registerIcons(): void {
	addIcon("mdi:menu", menu);
	addIcon("mdi:location", location);
	addIcon("mdi:copy", copy);
	addIcon("mdi:focus", focus);
	addIcon("mdi:fareZone", fareZone);
	addIcon("mdi:directions", directions);
	addIcon("mdi:schedule", schedule);
	addIcon("mdi:depot", depot);
	addIcon("mdi:duration", duration);
	addIcon("mdi:swap", swap);
	addIcon("mdi:refresh", refresh);
	addIcon("mdi:noDirections", noDirections);
	addIcon("mdi:interchange", interchange);
	addIcon("mdi:transfer", transfer);
	addIcon("mdi:check", check);
	addIcon("fluent:hidden", hidden);
	addIcon("fluent:solid", solid);
	addIcon("fluent:hollow", hollow);
	addIcon("fluent:dashed", dashed);
	addIcon("mdi:train", train);
	addIcon("mdi:lightRail", lightRail);
	addIcon("mdi:highSpeedRail", highSpeedRail);
	addIcon("mdi:ferry", ferry);
	addIcon("mdi:cruise", cruise);
	addIcon("mdi:fastFerry", fastFerry);
	addIcon("mdi:cableCar", cableCar);
	addIcon("mdi:bus", bus);
	addIcon("mdi:minibus", minibus);
	addIcon("mdi:expressBus", expressBus);
	addIcon("mdi:plane", plane);
	addIcon("mdi:clockwise", clockwise);
	addIcon("mdi:anticlockwise", anticlockwise);
	addIcon("mdi:walk", walk);
	addIcon("mdi:arrowDown", arrowDown);
}
