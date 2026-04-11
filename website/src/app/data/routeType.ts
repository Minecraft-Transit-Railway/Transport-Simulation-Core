export class RouteType {
	constructor(public readonly icon: string, public readonly text: string) {
	}
}

export const ROUTE_TYPES: Record<string, RouteType> = {
	train_normal: new RouteType("mdi:train", "Train"),
	train_light_rail: new RouteType("mdi:lightRail", "Light Rail"),
	train_high_speed: new RouteType("mdi:highSpeedRail", "High Speed"),
	boat_normal: new RouteType("mdi:ferry", "Ferry"),
	boat_light_rail: new RouteType("mdi:cruise", "Cruise"),
	boat_high_speed: new RouteType("mdi:fastFerry", "Fast Ferry"),
	cable_car_normal: new RouteType("mdi:cableCar", "Cable Car"),
	bus_normal: new RouteType("mdi:bus", "Bus"),
	bus_light_rail: new RouteType("mdi:minibus", "Minibus"),
	bus_high_speed: new RouteType("mdi:expressBus", "Express Bus"),
	airplane_normal: new RouteType("mdi:plane", "Plane"),
} as const;
