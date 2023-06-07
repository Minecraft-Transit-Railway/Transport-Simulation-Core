package org.mtr.core.tools;

import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;

public class CoolDown<T> {

	private final Long2IntAVLTreeMap map = new Long2IntAVLTreeMap();
	private final ObjectAVLTreeSet<T> forceUpdates = new ObjectAVLTreeSet<>();

	public void tick(ObjectAVLTreeSet<T> dataToUpdate, LongAVLTreeSet keysToRemove) {
		map.replaceAll((key, value) -> {
			if (value > 1) {
				keysToRemove.add(key.longValue());
				return 2;
			} else {
				return value + 1;
			}
		});
		keysToRemove.forEach(map::remove);
		dataToUpdate.addAll(forceUpdates);
		forceUpdates.clear();
	}

	public void update(T data, long key, boolean forceUpdate) {
		if (forceUpdate || !map.containsKey(key)) {
			forceUpdates.add(data);
		}
		map.put(key, 0);
	}
}
