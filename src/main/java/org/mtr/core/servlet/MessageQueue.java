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
			// Restore the interrupt flag so callers higher up the stack can react to the
			// interruption — the alternative is silently swallowing it (see CODE_STYLES §3.14).
			Thread.currentThread().interrupt();
			Main.LOGGER.error("Interrupted while enqueuing message", e);
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
