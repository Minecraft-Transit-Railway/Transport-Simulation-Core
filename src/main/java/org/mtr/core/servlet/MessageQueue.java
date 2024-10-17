package org.mtr.core.servlet;

import org.mtr.core.Main;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

public final class MessageQueue<T> {

	private final LinkedBlockingDeque<T> linkedBlockingDeque = new LinkedBlockingDeque<>();

	public void put(T object) {
		try {
			linkedBlockingDeque.put(object);
		} catch (InterruptedException e) {
			Main.LOGGER.error("", e);
		}
	}

	public void process(Consumer<T> callback) {
		while (true) {
			final T object = linkedBlockingDeque.poll();
			if (object == null) {
				break;
			} else {
				callback.accept(object);
			}
		}
	}
}
