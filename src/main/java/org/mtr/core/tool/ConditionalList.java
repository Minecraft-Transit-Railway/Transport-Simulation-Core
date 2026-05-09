package org.mtr.core.tool;

/**
 * Marker interface for elements of an ordered list that can answer "do I match this scalar?".
 *
 * <p>Used by {@link Utilities#getIndexFromConditionalList(java.util.List, double)} to binary-search
 * a sorted list for the largest index whose element matches the supplied value (typically a
 * timestamp / rail-progress / hour). Implementations are expected to be monotonically partitioned
 * so the binary search converges.</p>
 */
public interface ConditionalList {

	/**
	 * @param value scalar to test against this element's range
	 * @return whether this element should be considered "still in range" for {@code value}
	 */
	boolean matchesCondition(double value);
}
