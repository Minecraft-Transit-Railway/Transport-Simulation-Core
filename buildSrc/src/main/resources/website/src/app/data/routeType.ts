export class RouteType {
	constructor(public readonly icon: string, public readonly text: string) {
	}
}

export const ROUTE_TYPES: { [key: string]: RouteType } = {
	train_normal: new RouteType("directions_railway", "Train"),
	train_light_rail: new RouteType("tram", "Light Rail"),
	train_high_speed: new RouteType("train", "High Speed"),
	boat_normal: new RouteType("sailing", "Ferry"),
	boat_light_rail: new RouteType("directions_boat", "Cruise"),
	boat_high_speed: new RouteType("snowmobile", "Fast Ferry"),
	cable_car_normal: new RouteType("airline_seat_recline_extra", "Cable Car"),
	bus_normal: new RouteType("directions_bus", "Bus"),
	bus_light_rail: new RouteType("local_taxi", "Minibus"),
	bus_high_speed: new RouteType("airport_shuttle", "Express Bus"),
	airplane_normal: new RouteType("flight", "Plane"),
} as const;
