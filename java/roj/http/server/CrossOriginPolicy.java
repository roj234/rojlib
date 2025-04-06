package roj.http.server;

import roj.http.HttpUtil;
import roj.text.TextUtil;

import java.util.Locale;
import java.util.Set;

/**
 * @author Roj234
 * @since 2025/4/28 0028 6:09
 */
public class CrossOriginPolicy {
	// 大写的方法
	public Set<String> allowMethods;
	// 小写的请求头
	public Set<String> allowHeaders;
	public Set<String> allowOrigins;
	public Set<String> exposeHeaders;
	public boolean allowCredentials;
	public int age;

	public boolean enforceOriginLimit;

	public Content simpleRequest(Request req) {
		String origin = req.get("origin");
		if (allowOrigins == null || allowOrigins.contains(origin)) {
			if (!allowCredentials) {
				req.responseHeader.put("access-control-allow-origin", "*");
			} else {
				// 规范要求具体的值
				req.responseHeader.put("access-control-allow-origin", req.get("origin"));
				req.responseHeader.put("access-control-allow-credentials", "true");
				req.responseHeader.add("vary", "origin");
			}
		} else {
			if (enforceOriginLimit && req.action() != HttpUtil.GET) {
				req.server().code(403);
				return Content.EMPTY;
			}
		}

		return null;
	}

	public boolean preflightRequest(Request req)  {
		String origin = req.get("origin");
		if (allowOrigins != null && !allowOrigins.contains(origin)) return false;

		String method = req.get("access-control-request-method");
		if (allowMethods != null && !allowMethods.contains(method)) return false;

		String headers = req.get("access-control-request-headers");
		if (headers != null) {
			for (String header : TextUtil.split(headers, ',')) {
				if (allowHeaders == null || !allowHeaders.contains(header.trim().toLowerCase(Locale.ROOT)))
					return false;
			}
		}

		var rh = req.responseHeader;

		if (exposeHeaders != null) {
			rh.put("access-control-expose-headers", exposeHeaders.isEmpty() ? "*" : TextUtil.join(exposeHeaders, ", "));
		}

		if (allowCredentials) {
			rh.put("access-control-allow-origin", req.get("origin"));
			rh.add("vary", "origin");

			rh.put("access-control-allow-credentials", "true");

			if (allowHeaders == null) {
				rh.put("access-control-allow-headers", headers);
				rh.add("vary", "access-control-request-headers");
			} else {
				rh.put("access-control-allow-headers", TextUtil.join(allowHeaders, ", "));
			}

			if (allowMethods == null) {
				rh.put("access-control-allow-methods", method);
				rh.add("vary", "access-control-request-method");
			} else {
				rh.put("access-control-allow-methods", TextUtil.join(allowMethods, ", "));
			}
		} else {
			if (allowOrigins == null) rh.put("access-control-allow-origin", "*");
			else {
				rh.put("access-control-allow-origin", req.get("origin"));
				rh.add("vary", "origin");
			}

			rh.put("access-control-allow-headers", allowHeaders == null ? "*" : TextUtil.join(allowHeaders, ", "));
			rh.put("access-control-allow-methods", allowMethods == null ? "*" : TextUtil.join(allowMethods, ", "));
		}
		rh.put("access-control-max-age", Integer.toString(age == 0 ? 600 : age));
		return true;
	}
}
