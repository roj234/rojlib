package roj.plugin;

import roj.plugin.di.DIContext;
import roj.plugin.di.DependencyProvider;
import roj.text.logging.Logger;

/**
 * @author Roj234
 * @since 2025/06/20 19:58
 */
public final class MyDependencyProvider {
	@DependencyProvider
	public static Logger logger(DIContext context) {
		return Logger.getLogger(context.owner().getSimpleName());
	}
}
