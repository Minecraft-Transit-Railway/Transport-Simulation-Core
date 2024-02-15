package org.mtr.core.data;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;

public class ClientData extends Data {

	public final ObjectAVLTreeSet<SimplifiedRoute> simplifiedRoutes = new ObjectAVLTreeSet<>();
	public final LongArrayList simplifiedRouteIds = new LongArrayList();

	@Override
	public void sync() {
		super.sync();
		simplifiedRouteIds.clear();
		simplifiedRoutes.forEach(simplifiedRoute -> simplifiedRouteIds.add(simplifiedRoute.getId()));
	}
}
