package org.mtr.core.servlet;

public enum HttpResponseStatus {

	OK(200, "OK"),
	BAD_REQUEST(400, "Bad Request"),
	NOT_FOUND(404, "Not Found"),
	INTERNAL_SERVER_ERROR(500, "Internal Server Error");

	public final int code;
	public final String description;

	HttpResponseStatus(int code, String description) {
		this.code = code;
		this.description = description;
	}
}
