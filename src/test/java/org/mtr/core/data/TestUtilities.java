package org.mtr.core.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.mtr.core.Main;
import org.mtr.core.client.Client;
import org.mtr.core.client.ClientGroup;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.serializers.MessagePackReader;
import org.mtr.core.serializers.MessagePackWriter;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TestUtilities {

	Path TEST_DIRECTORY = Paths.get("build/test-data");
	int PORT = 8888;
	Random RANDOM = new Random();

	static <T extends SerializedDataBase> T getDataFromJsonObject(JsonObject jsonObject, Function<ReaderBase, T> newInstance) {
		return newInstance.apply(new JsonReader(jsonObject));
	}

	static String prettyPrint(JsonObject jsonObject) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject).replace("  ", "\t");
	}

	static <T extends SerializedDataBase> void compareObjects(T data1, T data2) {
		Assertions.assertEquals(prettyPrint(Utilities.getJsonObjectFromData(data1)), prettyPrint(Utilities.getJsonObjectFromData(data2)));
		Assertions.assertEquals(data1.toString(), data2.toString());
	}

	static Simulator getDefaultSimulator() {
		return new Simulator("test", TEST_DIRECTORY, 1200000, 0);
	}

	static JsonObject sendHttpDataRequest(String endpoint, JsonObject bodyObject) {
		return sendHttpRequest(String.format("http://localhost:%s/mtr/api/data/%s", PORT, endpoint), bodyObject);
	}

	static JsonObject sendHttpRequest(String uri, JsonObject bodyObject) {
		final HttpUriRequest httpUriRequest = bodyObject == null ? new HttpGet(uri) : new HttpPost(uri);

		if (httpUriRequest instanceof HttpPost) {
			try {
				((HttpPost) httpUriRequest).setEntity(new StringEntity(bodyObject.toString()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		JsonObject responseObject = new JsonObject();

		try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
			try (final CloseableHttpResponse response = httpClient.execute(httpUriRequest)) {
				responseObject = JsonParser.parseString(EntityUtils.toString(response.getEntity())).getAsJsonObject();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return responseObject;
	}

	static <T extends SerializedDataBase> void serializeAndDeserialize(T data, Function<ReaderBase, T> newInstance) {
		final JsonObject jsonObject = Utilities.getJsonObjectFromData(data);
		Main.LOGGER.info(prettyPrint(jsonObject));
		compareObjects(data, getDataFromJsonObject(jsonObject, newInstance));

		try (final MessageBufferPacker messageBufferPacker = MessagePack.newDefaultBufferPacker()) {
			final MessagePackWriter messagePackWriter = new MessagePackWriter(messageBufferPacker);
			data.serializeData(messagePackWriter);
			messagePackWriter.serialize();
			try (final MessageUnpacker messageUnpacker = MessagePack.newDefaultUnpacker(messageBufferPacker.toByteArray())) {
				compareObjects(data, newInstance.apply(new MessagePackReader(messageUnpacker)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static String randomString() {
		return Utilities.numberToPaddedHexString(RANDOM.nextLong());
	}

	static <T extends Enum<T>> T randomEnum(T[] values) {
		return values[RANDOM.nextInt(values.length)];
	}

	static <T> ObjectArrayList<T> randomList(Supplier<T> supplier) {
		final ObjectArrayList<T> list = new ObjectArrayList<>();
		randomLoop(() -> list.add(supplier.get()));
		return list;
	}

	static void randomLoop(Runnable runnable) {
		if (RANDOM.nextBoolean()) {
			final int count = RANDOM.nextInt(10) + 1;
			for (int i = 0; i < count; i++) {
				runnable.run();
			}
		}
	}

	static TransportMode randomTransportMode() {
		return randomEnum(TransportMode.values());
	}

	static Client randomClient() {
		return new Client(UUID.randomUUID());
	}

	static ClientGroup randomClientGroup() {
		return new ClientGroup();
	}

	static Depot randomDepot() {
		return new Depot(randomTransportMode(), getDefaultSimulator());
	}

	static InterchangeRouteNamesForColor randomInterchangeRouteNamesForColor() {
		return new InterchangeRouteNamesForColor(RANDOM.nextLong());
	}

	static InterchangeColorsForStationName randomInterchangeColorsForStationName() {
		return new InterchangeColorsForStationName(randomString());
	}

	static Lift randomLift() {
		return new Lift(getDefaultSimulator());
	}

	static PathData randomPathData() {
		return new PathData(randomRail(), RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextDouble(), RANDOM.nextDouble(), randomPosition(), randomPosition());
	}

	static Platform randomPlatform() {
		return new Platform(randomPosition(), randomPosition(), randomTransportMode(), getDefaultSimulator());
	}

	static Position randomPosition() {
		return new Position(RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextLong());
	}

	static RailNodeConnection randomRailNodeConnection() {
		return new RailNodeConnection(randomPosition(), randomRail());
	}

	static RailNode randomRailNode() {
		return new RailNode(randomPosition());
	}

	static Rail randomRail() {
		return Rail.newRail(randomPosition(), randomEnum(Angle.values()), randomPosition(), randomEnum(Angle.values()), RANDOM.nextLong(), randomEnum(Rail.Shape.values()), randomEnum(Rail.Shape.values()), RANDOM.nextBoolean(), RANDOM.nextBoolean(), RANDOM.nextBoolean(), randomTransportMode());
	}

	static Route randomRoute() {
		return new Route(randomTransportMode(), getDefaultSimulator());
	}

	static RoutePlatformData randomRoutePlatformData() {
		return new RoutePlatformData(RANDOM.nextLong());
	}

	static Siding randomSiding() {
		return new Siding(randomPosition(), randomPosition(), RANDOM.nextDouble(), randomTransportMode(), getDefaultSimulator());
	}

	static Station randomStation() {
		return new Station(getDefaultSimulator());
	}

	static VehicleCar randomVehicleCar() {
		return new VehicleCar(randomString(), RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble());
	}

	static VehicleExtraData randomVehicleExtraData() {
		return VehicleExtraData.create(RANDOM.nextDouble(), randomList(TestUtilities::randomVehicleCar), randomList(TestUtilities::randomPathData), randomList(TestUtilities::randomPathData), randomList(TestUtilities::randomPathData), randomPathData(), RANDOM.nextBoolean(), RANDOM.nextDouble(), RANDOM.nextBoolean(), RANDOM.nextDouble(), RANDOM.nextLong());
	}

	static Vehicle randomVehicle() {
		return new Vehicle(randomVehicleExtraData(), randomSiding(), randomTransportMode(), getDefaultSimulator());
	}

	static Client newClient(ReaderBase readerBase) {
		return new Client(readerBase);
	}

	static ClientGroup newClientGroup(ReaderBase readerBase) {
		return new ClientGroup(readerBase);
	}

	static Depot newDepot(ReaderBase readerBase) {
		return new Depot(readerBase, getDefaultSimulator());
	}

	static InterchangeRouteNamesForColor newInterchangeRouteNamesForColor(ReaderBase readerBase) {
		return new InterchangeRouteNamesForColor(readerBase);
	}

	static InterchangeColorsForStationName newInterchangeColorsForStationName(ReaderBase readerBase) {
		return new InterchangeColorsForStationName(readerBase);
	}

	static Lift newLift(ReaderBase readerBase) {
		return new Lift(readerBase, getDefaultSimulator());
	}

	static PathData newPathData(ReaderBase readerBase) {
		return new PathData(readerBase);
	}

	static Platform newPlatform(ReaderBase readerBase) {
		return new Platform(readerBase, getDefaultSimulator());
	}

	static Position newPosition(ReaderBase readerBase) {
		return new Position(readerBase);
	}

	static RailNodeConnection newRailNodeConnection(ReaderBase readerBase) {
		return new RailNodeConnection(readerBase);
	}

	static RailNode newRailNode(ReaderBase readerBase) {
		return new RailNode(readerBase);
	}

	static Rail newRail(ReaderBase readerBase) {
		return new Rail(readerBase);
	}

	static Route newRoute(ReaderBase readerBase) {
		return new Route(readerBase, getDefaultSimulator());
	}

	static RoutePlatformData newRoutePlatformData(ReaderBase readerBase) {
		return new RoutePlatformData(readerBase);
	}

	static Siding newSiding(ReaderBase readerBase) {
		return new Siding(readerBase, getDefaultSimulator());
	}

	static Station newStation(ReaderBase readerBase) {
		return new Station(readerBase, getDefaultSimulator());
	}

	static VehicleCar newVehicleCar(ReaderBase readerBase) {
		return new VehicleCar(readerBase);
	}

	static VehicleExtraData newVehicleExtraData(ReaderBase readerBase) {
		return new VehicleExtraData(readerBase);
	}

	static Vehicle newVehicle(ReaderBase readerBase) {
		return new Vehicle(randomVehicleExtraData(), randomSiding(), readerBase, getDefaultSimulator());
	}
}
