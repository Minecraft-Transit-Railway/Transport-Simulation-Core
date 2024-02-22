package org.mtr.core.path;

import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public abstract class PathFinder<T> {

	private long totalTime = Long.MAX_VALUE;
	private boolean completed;

	protected final T startNode;
	protected final T endNode;
	private final Object2LongOpenHashMap<T> globalBlacklist = new Object2LongOpenHashMap<>();
	private final Object2LongOpenHashMap<T> localBlacklist = new Object2LongOpenHashMap<>();
	private final ObjectArrayList<ConnectionDetails<T>> tempData;
	private final ObjectArrayList<ConnectionDetails<T>> data = new ObjectArrayList<>();

	public PathFinder(T startNode, T endNode) {
		this.startNode = startNode;
		this.endNode = endNode;
		completed = startNode.equals(endNode);
		tempData = ObjectArrayList.of(new ConnectionDetails<>(startNode, 0, 0, 0));
	}

	protected ObjectArrayList<ConnectionDetails<T>> findPath() {
		if (!completed) {
			final long elapsedTime = tempData.stream().mapToLong(data -> data.duration).sum();
			final ConnectionDetails<T> prevConnectionDetails = Utilities.getElement(tempData, -1);
			final T prevNode = prevConnectionDetails == null ? startNode : prevConnectionDetails.node;

			T bestNode = null;
			long bestIncrease = Long.MIN_VALUE;
			long bestDuration = 0;
			long bestWaitingTime = 0;
			long bestRouteId = 0;

			for (final ConnectionDetails<T> connectionDetails : getConnections(elapsedTime, prevNode)) {
				final T thisNode = connectionDetails.node;
				final long duration = connectionDetails.duration;
				final long waitingTime = connectionDetails.waitingTime;
				final long totalDuration = duration + waitingTime;

				if (verifyTime(thisNode, elapsedTime + totalDuration)) {
					final long increase = (getWeightFromEndNode(prevNode) - getWeightFromEndNode(thisNode)) / totalDuration;
					globalBlacklist.put(thisNode, elapsedTime + totalDuration);
					if (increase > bestIncrease) {
						bestNode = thisNode;
						bestIncrease = increase;
						bestDuration = duration;
						bestWaitingTime = waitingTime;
						bestRouteId = connectionDetails.routeId;
					}
				}
			}

			if (bestNode == null || bestDuration == 0) {
				if (tempData.isEmpty()) {
					completed = true;
				} else {
					tempData.remove(tempData.size() - 1);
				}
			} else {
				final long totalDuration = elapsedTime + bestDuration + bestWaitingTime;
				localBlacklist.put(bestNode, totalDuration);
				tempData.add(new ConnectionDetails<>(bestNode, bestDuration, bestWaitingTime, bestRouteId));

				if (bestNode.equals(endNode)) {
					if (totalDuration > 0 && totalDuration < totalTime) {
						totalTime = totalDuration;
						data.clear();
						data.addAll(tempData);
					}

					tempData.clear();
					localBlacklist.clear();
				}
			}
		}

		return completed ? data : null;
	}

	protected abstract ObjectOpenHashSet<ConnectionDetails<T>> getConnections(long elapsedTime, T data);

	protected abstract long getWeightFromEndNode(T node);

	private boolean verifyTime(T node, long time) {
		return time < totalTime && compareBlacklist(localBlacklist, node, time, false) && compareBlacklist(globalBlacklist, node, time, true);
	}

	private static <U> boolean compareBlacklist(Object2LongOpenHashMap<U> blacklist, U node, long time, boolean lessThanOrEqualTo) {
		return !blacklist.containsKey(node) || (lessThanOrEqualTo ? time <= blacklist.getLong(node) : time < blacklist.getLong(node));
	}

	protected static class ConnectionDetails<T> {

		public final T node;
		public final long duration;
		public final long waitingTime;
		public final long routeId;

		protected ConnectionDetails(T node, long duration, long waitingTime, long routeId) {
			this.node = node;
			this.duration = duration;
			this.waitingTime = waitingTime;
			this.routeId = routeId;
		}
	}
}
