package roj.plugins.flow;

import roj.http.server.Request;
import roj.plugin.PermissionHolder;

/**
 * @author Roj234
 * @since 2025/10/10 18:53
 */
public interface GroupRule {
	String apply(PermissionHolder user, Request request);
}
