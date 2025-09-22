package org.mtr.core.tool;

import javax.annotation.Nullable;

/**
 * Represents a ticked object that can be refreshed.
 * After the object has been ticked after a set timeout, it is refreshed.
 * Refreshing can occur in multiple steps.
 */
public abstract class RefreshableObject<T> {

	private T data;
	private long expiryTime = 0;
	private int currentRefreshStep = -1;
	private long totalRefreshTime = 0;
	private long longestRefreshTime = 0;

	private final long timeout;

	/**
	 * @param initialValue the initial data value
	 * @param timeout      the number of milliseconds before the object must be refreshed
	 */
	protected RefreshableObject(T initialValue, long timeout) {
		data = initialValue;
		this.timeout = timeout;
	}

	/**
	 * Tick the object.
	 *
	 * @return if the object is currently being refreshed
	 */
	public final boolean tick() {
		final long millis = System.currentTimeMillis();

		if (millis > expiryTime) {
			currentRefreshStep = 0;
			totalRefreshTime = 0;
			longestRefreshTime = 0;
			expiryTime = Long.MAX_VALUE;
		}

		if (currentRefreshStep >= 0) {
			final T newData = refresh(currentRefreshStep);
			final long refreshTime = System.currentTimeMillis() - millis;
			totalRefreshTime += refreshTime;
			longestRefreshTime = Math.max(longestRefreshTime, refreshTime);
			currentRefreshStep++;

			if (newData != null) {
				data = newData;
				currentRefreshStep = -1;
				expiryTime = millis + timeout;
			}

			return true;
		} else {
			return false;
		}
	}

	/**
	 * Refreshes the object. This can be run as many times as needed until a non-null value is returned.
	 *
	 * @param currentRefreshStep the current run number
	 * @return the new object or {@code null} otherwise
	 */
	@Nullable
	public abstract T refresh(int currentRefreshStep);

	public T getData() {
		return data;
	}

	public long getTotalRefreshTime() {
		return totalRefreshTime;
	}

	public long getLongestRefreshTime() {
		return longestRefreshTime;
	}
}
