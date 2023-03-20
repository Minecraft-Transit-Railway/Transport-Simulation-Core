package org.mtr.core.servlet;

public enum ResponseCode {
	SUCCESS(200, "Success"),
	INVALID(400, "Invalid request: %s"),

	UNAUTHORIZED(401, "Unauthorized"),
	NOT_FOUND(404, "Not found: %s"),
	EXCEPTION(500, "Service exception");

	public final int code;
	private final String message;

	ResponseCode(int code, String message) {
		this.code = code;
		this.message = message;
	}

	public String getMessage(Object... parameters) {
		return String.format(message, parameters);
	}
}
