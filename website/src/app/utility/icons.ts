import {addIcon} from "iconify-icon";

// Static UI icons
import menu from "@iconify-icons/material-symbols/menu";
import myLocation from "@iconify-icons/material-symbols/my-location";
import contentCopy from "@iconify-icons/material-symbols/content-copy";
import filterCenterFocus from "@iconify-icons/material-symbols/filter-center-focus";
import sell from "@iconify-icons/material-symbols/sell";
import directions from "@iconify-icons/material-symbols/directions";
import schedule from "@iconify-icons/material-symbols/schedule";
import home from "@iconify-icons/material-symbols/home";
import hourglassEmpty from "@iconify-icons/material-symbols/hourglass-empty";
import swapVert from "@iconify-icons/material-symbols/swap-vert";
import refresh from "@iconify-icons/material-symbols/refresh";
import removeRoad from "@iconify-icons/material-symbols/remove-road";
import polyline from "@iconify-icons/material-symbols/polyline";
import check from "@iconify-icons/material-symbols/check";

// Visibility / interchange toggle icons
import visibilityOff from "@iconify-icons/material-symbols/visibility-off";
import horizontalRule from "@iconify-icons/material-symbols/horizontal-rule";
import dragHandle from "@iconify-icons/material-symbols/drag-handle";
import moreHoriz from "@iconify-icons/material-symbols/more-horiz";

// Route type icons (referenced dynamically via routeType.ts)
import directionsRailway from "@iconify-icons/material-symbols/directions-railway";
import tram from "@iconify-icons/material-symbols/tram";
import train from "@iconify-icons/material-symbols/train";
import sailing from "@iconify-icons/material-symbols/sailing";
import directionsBoat from "@iconify-icons/material-symbols/directions-boat";
import snowmobile from "@iconify-icons/material-symbols/snowmobile";
import airlineSeatReclineExtra from "@iconify-icons/material-symbols/airline-seat-recline-extra";
import directionsBus from "@iconify-icons/material-symbols/directions-bus";
import localTaxi from "@iconify-icons/material-symbols/local-taxi";
import airportShuttle from "@iconify-icons/material-symbols/airport-shuttle";
import flight from "@iconify-icons/material-symbols/flight";

// Circular state icons
import rotateRight from "@iconify-icons/material-symbols/rotate-right";
import rotateLeft from "@iconify-icons/material-symbols/rotate-left";

// Directions icons
import transferWithinAStation from "@iconify-icons/material-symbols/transfer-within-a-station";
import directionsWalk from "@iconify-icons/material-symbols/directions-walk";

// Misc icons
import arrowDownward from "@iconify-icons/material-symbols/arrow-downward";

/**
 * Pre-registers all Material Symbols icons used in the app so they are bundled
 * and available offline without CDN requests.
 */
export function registerIcons(): void {
	addIcon("material-symbols:menu", menu);
	addIcon("material-symbols:my-location", myLocation);
	addIcon("material-symbols:content-copy", contentCopy);
	addIcon("material-symbols:filter-center-focus", filterCenterFocus);
	addIcon("material-symbols:sell", sell);
	addIcon("material-symbols:directions", directions);
	addIcon("material-symbols:schedule", schedule);
	addIcon("material-symbols:home", home);
	addIcon("material-symbols:hourglass-empty", hourglassEmpty);
	addIcon("material-symbols:swap-vert", swapVert);
	addIcon("material-symbols:refresh", refresh);
	addIcon("material-symbols:remove-road", removeRoad);
	addIcon("material-symbols:polyline", polyline);
	addIcon("material-symbols:check", check);
	addIcon("material-symbols:visibility-off", visibilityOff);
	addIcon("material-symbols:horizontal-rule", horizontalRule);
	addIcon("material-symbols:drag-handle", dragHandle);
	addIcon("material-symbols:more-horiz", moreHoriz);
	addIcon("material-symbols:directions-railway", directionsRailway);
	addIcon("material-symbols:tram", tram);
	addIcon("material-symbols:train", train);
	addIcon("material-symbols:sailing", sailing);
	addIcon("material-symbols:directions-boat", directionsBoat);
	addIcon("material-symbols:snowmobile", snowmobile);
	addIcon("material-symbols:airline-seat-recline-extra", airlineSeatReclineExtra);
	addIcon("material-symbols:directions-bus", directionsBus);
	addIcon("material-symbols:local-taxi", localTaxi);
	addIcon("material-symbols:airport-shuttle", airportShuttle);
	addIcon("material-symbols:flight", flight);
	addIcon("material-symbols:rotate-right", rotateRight);
	addIcon("material-symbols:rotate-left", rotateLeft);
	addIcon("material-symbols:transfer-within-a-station", transferWithinAStation);
	addIcon("material-symbols:directions-walk", directionsWalk);
	addIcon("material-symbols:arrow-downward", arrowDownward);
}

