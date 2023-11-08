package org.mtr.core;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;

@ParametersAreNonnullByDefault
public class RuntimeTests implements TestUtilities {

	@Test
	public void createAndUpdateData() throws IOException {
		FileUtils.deleteDirectory(TEST_DIRECTORY.toFile());
		final Main main = new Main(TEST_DIRECTORY, PORT, "overworld");

		final Station station1 = new Station(TestUtilities.getDefaultSimulator());
		station1.setName("Test 1");
		station1.setCorners(new Position(10, -100, 10), new Position(20, 100, 20));

		final Station station2 = new Station(TestUtilities.getDefaultSimulator());
		station2.setName("Test 2");
		station2.setCorners(new Position(-10, -100, -10), new Position(-20, 100, -20));

		final Platform platform1 = new Platform(new Position(15, 0, 14), new Position(15, 0, 16), TransportMode.TRAIN, TestUtilities.getDefaultSimulator());
		final Platform platform2 = new Platform(new Position(-15, 0, -14), new Position(-15, 0, -16), TransportMode.TRAIN, TestUtilities.getDefaultSimulator());

		final JsonObject requestObject = new JsonObject();

		final JsonArray requestStationArray = new JsonArray();
		requestStationArray.add(Utilities.getJsonObjectFromData(station1));
		requestStationArray.add(Utilities.getJsonObjectFromData(station2));
		requestObject.add("stations", requestStationArray);

		final JsonArray requestPlatformArray = new JsonArray();
		requestPlatformArray.add(Utilities.getJsonObjectFromData(platform1));
		requestPlatformArray.add(Utilities.getJsonObjectFromData(platform2));
		requestObject.add("platforms", requestPlatformArray);

		final JsonObject responseObject1 = TestUtilities.sendHttpDataRequest("update", requestObject);
		System.out.println(Utilities.prettyPrint(responseObject1));

		main.stop();
	}
}
