package org.mtr.core.servlet;

import org.mtr.libraries.javax.servlet.AsyncContext;
import org.mtr.libraries.javax.servlet.http.HttpServlet;
import org.mtr.libraries.javax.servlet.http.HttpServletRequest;
import org.mtr.libraries.javax.servlet.http.HttpServletResponse;

import java.util.function.Function;

public final class WebServlet extends HttpServlet {

	private final Function<String, String> contentProvider;
	private final String expectedPath;

	public WebServlet(Function<String, String> contentProvider, String expectedPath) {
		this.contentProvider = contentProvider;
		this.expectedPath = expectedPath;
	}

	@Override
	protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
		final AsyncContext asyncContext = httpServletRequest.startAsync();
		asyncContext.setTimeout(0);

		final String requestUri = httpServletRequest.getRequestURI();
		if (requestUri.startsWith(expectedPath)) {
			final String path = ServletBase.removeLastSlash(requestUri.replace(expectedPath, ""));
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
