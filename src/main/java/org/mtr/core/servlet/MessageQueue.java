package org.mtr.core.servlet;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/**
 * Bounded-by-memory FIFO queue used to hand work off to the simulator thread.
 *
 * <p>Backed by a {@link LinkedBlockingDeque} so producers can {@link #put(Object) put} from any
 * thread without blocking the simulator. The simulator drains the queue once per tick via
 * {@link #process(Consumer)}.</p>
 *
 * @param <T> element type the queue holds
 */
@Log4j2
public final class MessageQueue<T> {

	private final LinkedBlockingDeque<T> linkedBlockingDeque = new LinkedBlockingDeque<>();

	/**
	 * Enqueue {@code object}. If the calling thread is interrupted while blocked, the interrupt
	 * flag is restored and the failure is logged so the caller can react instead of silently
	 * losing the message (see CODE_STYLES §3.14).
	 */
	public void put(T object) {
		try {
			linkedBlockingDeque.put(object);
		} catch (InterruptedException e) {
			// Restore the interrupt flag so callers higher up the stack can react to the
			// interruption — the alternative is silently swallowing it (see CODE_STYLES §3.14).
			Thread.currentThread().interrupt();
			log.error("Interrupted while enqueuing message", e);
		}
	}

	/**
	 * Drain every queued element on the calling thread, feeding each into {@code callback} in
	 * arrival order. Returns once the queue is empty.
	 */
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
