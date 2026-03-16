export class RouteType {
	constructor(public readonly icon: string, public readonly text: string) {
	}
}

export const ROUTE_TYPES: Record<string, RouteType> = {
	train_normal: new RouteType("directions-railway", "Train"),
	train_light_rail: new RouteType("tram", "Light Rail"),
	train_high_speed: new RouteType("train", "High Speed"),
	boat_normal: new RouteType("sail-boat", "Ferry"),
	boat_light_rail: new RouteType("boat", "Cruise"),
	boat_high_speed: new RouteType("rocket-launch", "Fast Ferry"),
	cable_car_normal: new RouteType("cable-car", "Cable Car"),
	bus_normal: new RouteType("bus", "Bus"),
	bus_light_rail: new RouteType("taxi", "Minibus"),
	bus_high_speed: new RouteType("airport-shuttle-alt", "Express Bus"),
	airplane_normal: new RouteType("airplane", "Plane"),
} as const;
