package roj.plugins.ci.plugin;

import roj.asm.util.Context;
import roj.plugins.ci.Compiler;

import java.io.File;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/19 0019 8:16
 */
public interface Plugin {
	String name();
	default void beforeCompile(Compiler compiler, List<String> options, List<File> source, PluginContext ctx) {}
	default void afterCompile(List<Context> data, PluginContext ctx) {}
	default void afterMap(List<Context> data, PluginContext ctx) {}
}