export class RouteType {
	constructor(public readonly icon: string, public readonly text: string) {
	}
}

export const ROUTE_TYPES: Record<string, RouteType> = {
	train_normal: new RouteType("directions-railway", "Train"),
	train_light_rail: new RouteType("tram", "Light Rail"),
	train_high_speed: new RouteType("train", "High Speed"),
	boat_normal: new RouteType("sailing", "Ferry"),
	boat_light_rail: new RouteType("directions-boat", "Cruise"),
	boat_high_speed: new RouteType("snowmobile", "Fast Ferry"),
	cable_car_normal: new RouteType("airline-seat-recline-extra", "Cable Car"),
	bus_normal: new RouteType("directions-bus", "Bus"),
	bus_light_rail: new RouteType("local-taxi", "Minibus"),
	bus_high_speed: new RouteType("airport-shuttle", "Express Bus"),
	airplane_normal: new RouteType("flight", "Plane"),
} as const;
