package org.mtr.core.path;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public abstract class PathFinder<T, U> {

	private double totalTime = Double.MAX_VALUE;
	private boolean completed;

	protected final T startNode;
	protected final T endNode;
	private final Object2DoubleOpenHashMap<T> globalBlacklist = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleOpenHashMap<T> localBlacklist = new Object2DoubleOpenHashMap<>();
	private final ObjectArrayList<ConnectionDetails<T>> tempData = new ObjectArrayList<>();
	private final ObjectArrayList<ConnectionDetails<T>> data = new ObjectArrayList<>();

	public PathFinder(T startNode, T endNode) {
		this.startNode = startNode;
		this.endNode = endNode;
		completed = startNode == endNode;
	}

	public abstract ObjectArrayList<U> tick();

	protected ObjectArrayList<ConnectionDetails<T>> findPath() {
		if (!completed) {
			final double elapsedTime = tempData.stream().mapToDouble(data -> data.duration).sum();
			final ConnectionDetails<T> prevConnectionDetails = tempData.isEmpty() ? null : tempData.get(tempData.size() - 1);
			final T prevNode = prevConnectionDetails == null ? startNode : prevConnectionDetails.node;

			T bestNode = null;
			double bestIncrease = -Double.MAX_VALUE;
			double bestDuration = 0;
			double bestWaitingTime = 0;
			long bestRouteId = 0;

			for (final ConnectionDetails<T> connectionDetails : getConnections(prevConnectionDetails)) {
				final T thisNode = connectionDetails.node;
				final double duration = connectionDetails.duration;
				final double waitingTime = connectionDetails.waitingTime;
				final double totalDuration = duration + waitingTime;

				if (verifyTime(thisNode, elapsedTime + totalDuration)) {
					final double increase = (getWeightFromEndNode(prevNode) - getWeightFromEndNode(thisNode)) / totalDuration;
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
				if (!tempData.isEmpty()) {
					tempData.remove(tempData.size() - 1);
				} else {
					completed = true;
				}
			} else {
				final double totalDuration = elapsedTime + bestDuration + bestWaitingTime;
				localBlacklist.put(bestNode, totalDuration);
				tempData.add(new ConnectionDetails<T>(bestNode, bestDuration, bestWaitingTime, bestRouteId));

				if (bestNode == endNode) {
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

	protected abstract ObjectAVLTreeSet<ConnectionDetails<T>> getConnections(ConnectionDetails<T> data);

	protected abstract double getWeightFromEndNode(T node);

	private boolean verifyTime(T node, double time) {
		return time < totalTime && compareBlacklist(localBlacklist, node, time, false) && compareBlacklist(globalBlacklist, node, time, true);
	}

	private static <U> boolean compareBlacklist(Object2DoubleOpenHashMap<U> blacklist, U node, double time, boolean lessThanOrEqualTo) {
		return !blacklist.containsKey(node) || (lessThanOrEqualTo ? time <= blacklist.getDouble(node) : time < blacklist.getDouble(node));
	}

	public static class ConnectionDetails<T> {

		public final T node;
		public final double duration;
		public final double waitingTime;
		public final long routeId;

		protected ConnectionDetails(T node, double duration, double waitingTime, long routeId) {
			this.node = node;
			this.duration = duration;
			this.waitingTime = waitingTime;
			this.routeId = routeId;
		}
	}
}
