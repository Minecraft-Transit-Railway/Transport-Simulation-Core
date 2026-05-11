package org.mtr.core.servlet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public abstract class WebServlet extends HttpServlet {

	private final Function<String, @Nullable String> contentProvider;
	private final String expectedPath;

	public WebServlet(Function<String, @Nullable String> contentProvider, String expectedPath) {
		this.contentProvider = contentProvider;
		this.expectedPath = expectedPath;
	}

	@Override
	protected final void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
		final AsyncContext asyncContext = httpServletRequest.startAsync();
		asyncContext.setTimeout(0);

		final String requestUri = httpServletRequest.getRequestURI();
		if (requestUri.startsWith(expectedPath)) {
			final String path = ServletBase.removeLastSlash(requestUri.replaceFirst(expectedPath, ""));
			final String newPath = path.isEmpty() ? "index.html" : path;
			final String content = contentProvider.apply(newPath);

			if (content == null) {
				ServletBase.sendResponse(httpServletResponse, asyncContext, "..", "", HttpResponseStatus.REDIRECT);
			} else {
				ServletBase.sendResponse(httpServletResponse, asyncContext, content, ServletBase.getMimeType(newPath), HttpResponseStatus.OK);
			}
		} else {
			ServletBase.sendResponse(httpServletResponse, asyncContext, "..", "", HttpResponseStatus.REDIRECT);
		}
	}
}
