package roj.ci.plugin;

import roj.collect.ArrayList;
import roj.config.node.ConfigValue;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/19 8:16
 */
public interface Processor {
	String name();
	default void init(ConfigValue config) {}
	default InputStream wrapResource(String path, InputStream in) {return in;}
	default int beforeCompile(ArrayList<String> options, List<File> sources, BuildContext ctx) {return 2;}
	default void afterCompile(BuildContext ctx) {}

	default boolean defaultEnabled() {return false;}
}