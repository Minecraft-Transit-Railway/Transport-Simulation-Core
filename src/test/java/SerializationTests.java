import org.mtr.core.tools.Utilities;

public class SerializationTests implements Utilities, TestUtilities {

//	@RepeatedTest(10)
//	public void stationSerialization() {
//		final Station station = new Station(TestUtilities.getDefaultSimulator());
//		Assertions.assertEquals("", station.name);
//		station.name = TestUtilities.randomString();
//		station.color = TestUtilities.randomColor();
//		station.zone = RANDOM.nextInt();
//		setCorners(station);
//
//		TestUtilities.randomLoop(() -> {
//			final ObjectArrayList<String> destinations = new ObjectArrayList<>();
//			station.exits.put(String.format("%s%s", TestUtilities.randomLetter(), TestUtilities.randomDigit()), destinations);
//			TestUtilities.randomLoop(() -> destinations.add(TestUtilities.randomString()));
//		});
//
//		serializeAndDeserialize(station, Station::new);
//	}
//
//	@RepeatedTest(10)
//	public void platformSerialization() {
//		final Platform platform = new Platform(TestUtilities.randomTransportMode(), TestUtilities.randomPosition(), TestUtilities.randomPosition(), TestUtilities.getDefaultSimulator());
//		Assertions.assertEquals("1", platform.name);
//		platform.name = TestUtilities.randomString();
//		platform.color = TestUtilities.randomColor();
//		setTimeValue(platform);
//		serializeAndDeserialize(platform, Platform::new);
//	}
//
//	@RepeatedTest(10)
//	public void sidingSerialization() {
//		final int railLength = RANDOM.nextInt(100);
//		final Siding siding = new Siding(TestUtilities.randomTransportMode(), TestUtilities.randomPosition(), TestUtilities.randomPosition(), railLength, TestUtilities.getDefaultSimulator());
//		Assertions.assertEquals("1", siding.name);
//		siding.name = TestUtilities.randomString();
//		siding.color = TestUtilities.randomColor();
//		siding.maxManualSpeed = RANDOM.nextDouble();
//		final double acceleration = RANDOM.nextDouble() * Vehicle.MAX_ACCELERATION * 2;
//		siding.setAcceleration(acceleration);
//		Assertions.assertEquals(siding.transportMode.continuousMovement ? Vehicle.MAX_ACCELERATION : Vehicle.roundAcceleration(acceleration), TestUtilities.getJsonObjectFromData(siding).get("acceleration").getAsDouble());
//		setTimeValue(siding);
//
//		if (RANDOM.nextInt(3) > 0) {
//			final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
//			double totalLength = 0;
//
//			while (true) {
//				final double length = RANDOM.nextDouble() * 10;
//				totalLength += length;
//
//				if (totalLength > railLength && RANDOM.nextBoolean()) {
//					break;
//				}
//
//				final double bogiePosition1 = RANDOM.nextDouble();
//				vehicleCars.add(new VehicleCar(
//						TestUtilities.randomString(),
//						length,
//						RANDOM.nextDouble(),
//						bogiePosition1,
//						RANDOM.nextBoolean() ? bogiePosition1 : RANDOM.nextDouble()
//				));
//
//				if (totalLength > railLength) {
//					break;
//				}
//			}
//
//			siding.setVehicleCars(vehicleCars);
//		}
//
//		switch (RANDOM.nextInt(5)) {
//			case 0:
//				siding.setUnlimitedVehicles(true);
//				Assertions.assertTrue(siding.getIsUnlimited());
//				Assertions.assertFalse(siding.getIsManual());
//				Assertions.assertEquals(0, siding.getMaxVehicles());
//				break;
//			case 1:
//				siding.setUnlimitedVehicles(false);
//				Assertions.assertFalse(!siding.transportMode.continuousMovement && siding.getIsUnlimited());
//				Assertions.assertFalse(siding.getIsManual());
//				Assertions.assertEquals(siding.transportMode.continuousMovement ? 0 : 1, siding.getMaxVehicles());
//				break;
//			case 2:
//				siding.setIsManual(true);
//				Assertions.assertFalse(!siding.transportMode.continuousMovement && siding.getIsUnlimited());
//				Assertions.assertTrue(siding.transportMode.continuousMovement || siding.getIsManual());
//				Assertions.assertEquals(siding.transportMode.continuousMovement ? 0 : 1, siding.getMaxVehicles());
//				break;
//			case 3:
//				siding.setIsManual(false);
//				Assertions.assertFalse(!siding.transportMode.continuousMovement && siding.getIsUnlimited());
//				Assertions.assertFalse(siding.getIsManual());
//				Assertions.assertEquals(siding.transportMode.continuousMovement ? 0 : 1, siding.getMaxVehicles());
//				break;
//			case 4:
//				final int maxVehicles = RANDOM.nextInt(3);
//				siding.setMaxVehicles(maxVehicles);
//				Assertions.assertFalse(!siding.transportMode.continuousMovement && siding.getIsUnlimited());
//				Assertions.assertFalse(siding.getIsManual());
//				Assertions.assertEquals(siding.transportMode.continuousMovement ? 0 : Math.max(1, maxVehicles), siding.getMaxVehicles());
//				break;
//		}
//
//		serializeAndDeserialize(siding, Siding::new);
//	}
//
//	@RepeatedTest(10)
//	public void routeSerialization() {
//		final Route route = new Route(TestUtilities.randomTransportMode(), TestUtilities.getDefaultSimulator());
//		Assertions.assertEquals("", route.name);
//		route.name = TestUtilities.randomString();
//		route.color = TestUtilities.randomColor();
//		route.hasRouteNumber = RANDOM.nextBoolean();
//		route.isHidden = RANDOM.nextBoolean();
//		route.circularState = TestUtilities.randomEnum(Route.CircularState.values());
//		route.routeNumber = TestUtilities.randomString();
//
//		TestUtilities.randomLoop(() -> {
//			final Route.RoutePlatform routePlatform = new Route.RoutePlatform(RANDOM.nextLong());
//			route.routePlatforms.add(routePlatform);
//			if (RANDOM.nextBoolean()) {
//				routePlatform.customDestination = TestUtilities.randomString();
//			}
//		});
//
//		serializeAndDeserialize(route, Route::new);
//	}
//
//	@RepeatedTest(10)
//	public void depotSerialization() {
//		final Depot depot = new Depot(TestUtilities.randomTransportMode(), TestUtilities.getDefaultSimulator());
//		Assertions.assertEquals("", depot.name);
//		depot.name = TestUtilities.randomString();
//		depot.color = TestUtilities.randomColor();
//		depot.useRealTime = RANDOM.nextBoolean();
//		depot.repeatInfinitely = RANDOM.nextBoolean();
//		depot.cruisingAltitude = RANDOM.nextInt();
//		setCorners(depot);
//		TestUtilities.randomLoop(() -> depot.routeIds.add(RANDOM.nextLong()));
//		TestUtilities.randomLoop(() -> depot.realTimeDepartures.add(RANDOM.nextInt()));
//
//		for (int i = 0; i < HOURS_PER_DAY; i++) {
//			final int frequency = RANDOM.nextInt();
//			depot.setFrequency(i, frequency);
//			Assertions.assertEquals(Math.max(0, frequency), TestUtilities.getJsonObjectFromData(depot).getAsJsonArray("frequencies").get(i).getAsInt());
//		}
//
//		serializeAndDeserialize(depot, Depot::new);
//	}
//
//	private static <T extends SerializedDataBase> void serializeAndDeserialize(T data, BiFunction<ReaderBase, Simulator, T> newInstance) {
//		final JsonObject jsonObject = TestUtilities.getJsonObjectFromData(data);
//		Main.LOGGER.info(TestUtilities.prettyPrint(jsonObject));
//		TestUtilities.compareObjects(data, TestUtilities.getDataFromJsonObject(jsonObject, newInstance));
//
//		try (final MessageBufferPacker messageBufferPacker = MessagePack.newDefaultBufferPacker()) {
//			final MessagePackWriter messagePackWriter = new MessagePackWriter(messageBufferPacker);
//			data.serializeData(messagePackWriter);
//			messagePackWriter.serialize();
//			try (final MessageUnpacker messageUnpacker = MessagePack.newDefaultUnpacker(messageBufferPacker.toByteArray())) {
//				TestUtilities.compareObjects(data, newInstance.apply(new MessagePackReader(messageUnpacker), TestUtilities.getDefaultSimulator()));
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void setCorners(T area) {
//		area.setCorners(RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextLong());
//		final JsonObject jsonObject = TestUtilities.getJsonObjectFromData(area);
//		Assertions.assertTrue(jsonObject.get("x_min").getAsLong() <= jsonObject.get("x_max").getAsLong());
//		Assertions.assertTrue(jsonObject.get("z_min").getAsLong() <= jsonObject.get("z_max").getAsLong());
//	}
//
//	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void setTimeValue(U savedRail) {
//		final int timeValue = RANDOM.nextInt(600000) + 1;
//		savedRail.setTimeValue(timeValue);
//		Assertions.assertEquals(savedRail.transportMode.continuousMovement ? 1 : timeValue, savedRail.getTimeValueMillis());
//	}
}
