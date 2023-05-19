import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.mtr.core.data.SerializedDataBase;
import org.mtr.core.data.TransportMode;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.serializers.JsonWriter;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.function.BiFunction;

public interface TestUtilities {

	Path TEST_DIRECTORY = Paths.get("build/test-data");
	int PORT = 8888;
	Random RANDOM = new Random();

	static <T extends SerializedDataBase> JsonObject getJsonObjectFromData(T data) {
		final JsonObject jsonObject = new JsonObject();
		data.serializeData(new JsonWriter(jsonObject));
		return jsonObject;
	}

	static <T extends SerializedDataBase> T getDataFromJsonObject(JsonObject jsonObject, BiFunction<ReaderBase, Simulator, T> newInstance) {
		return newInstance.apply(new JsonReader(jsonObject), getDefaultSimulator());
	}

	static String prettyPrint(JsonObject jsonObject) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject);
	}

	static <T extends SerializedDataBase> void compareObjects(T data1, T data2) {
		Assertions.assertEquals(prettyPrint(getJsonObjectFromData(data1)), prettyPrint(getJsonObjectFromData(data2)));
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

	static String randomString() {
		return Utilities.numberToPaddedHexString(RANDOM.nextLong());
	}

	static int randomColor() {
		return RANDOM.nextInt(0x1000000);
	}

	static char randomLetter() {
		return (char) (RANDOM.nextInt(26) + 'A');
	}

	static char randomDigit() {
		return (char) (RANDOM.nextInt(10) + '0');
	}

	static <T extends Enum<T>> T randomEnum(T[] values) {
		return values[RANDOM.nextInt(values.length)];
	}

	static double[] randomDoubleArray(int length) {
		final double[] array = new double[length];
		for (int i = 0; i < length; i++) {
			array[i] = RANDOM.nextDouble() * length;
		}
		return array;
	}

	static TransportMode randomTransportMode() {
		return randomEnum(TransportMode.values());
	}

	static Position randomPosition() {
		return new Position(RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextLong());
	}

	static void randomLoop(Runnable runnable) {
		if (RANDOM.nextBoolean()) {
			final int count = RANDOM.nextInt(10) + 1;
			for (int i = 0; i < count; i++) {
				runnable.run();
			}
		}
	}
}
