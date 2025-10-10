package roj.ci.plugin;

import roj.ci.BuildContext;
import roj.collect.ArrayList;
import roj.config.node.ConfigValue;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/19 8:16
 */
public interface Plugin {
	default String name() {return getClass().getName();}
	default void init(ConfigValue config) {}
	default InputStream wrapResource(String path, InputStream in) {return in;}
	/**
	 *
	 * @param options
	 * @param sources
	 * @param ctx
	 * @return needLoadContext
	 */
	default boolean preProcess(ArrayList<String> options, List<File> sources, BuildContext ctx) {return false;}
	default void process(BuildContext ctx) {}
	default void postProcess(BuildContext ctx) {}

	default boolean defaultEnabled() {return false;}
}