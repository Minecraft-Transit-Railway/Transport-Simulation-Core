package org.mtr.core.servlet;

import org.mtr.core.generated.WebserverResources;
import org.mtr.libraries.io.netty.handler.codec.http.HttpResponseStatus;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class WebServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
		final AsyncContext asyncContext = httpServletRequest.startAsync();
		asyncContext.setTimeout(0);
		getContent(httpServletResponse, asyncContext, httpServletRequest.getServletPath(), true);
	}

	private static void getContent(HttpServletResponse httpServletResponse, AsyncContext asyncContext, String path, boolean retry) {
		final String content = WebserverResources.get(ServletBase.removeLastSlash(path));
		if (content == null) {
			if (retry) {
				getContent(httpServletResponse, asyncContext, "index.html", false);
			} else {
				ServletBase.sendResponse(httpServletResponse, asyncContext, "", "", HttpResponseStatus.NOT_FOUND);
			}
		} else {
			ServletBase.sendResponse(httpServletResponse, asyncContext, content, ServletBase.getMimeType(path), HttpResponseStatus.OK);
		}
	}
}
