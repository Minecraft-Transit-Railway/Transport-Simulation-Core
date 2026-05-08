/**
 * Per-dimension simulator: ticks the data model on its own scheduled thread (or under host control), drains the inbound message queue, persists state to disk via {@link org.mtr.core.simulation.FileLoader}, and exposes per-dimension save / load lifecycle to {@link org.mtr.core.Main}.
 */
@NullMarked
package org.mtr.core.simulation;

import org.jspecify.annotations.NullMarked;
