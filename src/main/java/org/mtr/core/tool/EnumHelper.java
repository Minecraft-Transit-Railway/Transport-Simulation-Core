package org.mtr.core.tool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tolerant {@link Enum#valueOf(Class, String)} replacement that returns a default instead of
 * throwing when the supplied {@code name} does not match any constant.
 *
 * <p>Used by deserialisers when reading enum-typed fields from on-disk data — schema evolution
 * may introduce or remove enum constants between builds, and {@link IllegalArgumentException}
 * propagating out of a load is never the right answer.</p>
 */
public interface EnumHelper {

	Logger LOGGER = LogManager.getLogger(EnumHelper.class);

	/**
	 * @param defaultValue value returned (and used to obtain the enum class) if {@code name} does
	 *                     not match any constant
	 * @param name         enum constant name to resolve
	 * @param <T>          enum type
	 * @return the matching enum constant, or {@code defaultValue} if none
	 */
	static <T extends Enum<T>> T valueOf(T defaultValue, String name) {
		try {
			return Enum.valueOf(defaultValue.getDeclaringClass(), name);
		} catch (Exception e) {
			// Schema drift — name no longer exists. Logged at trace because it's expected during
			// legacy data loads (CODE_STYLES §3.14).
			LOGGER.trace("EnumHelper.valueOf({}, {}) falling back to default", defaultValue.getDeclaringClass().getSimpleName(), name, e);
			return defaultValue;
		}
	}
}
