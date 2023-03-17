package roj.mod.plugin;

import roj.asm.util.Context;

import java.io.File;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/19 0019 8:16
 */
public interface Plugin {
	String name();
	default void beforeCompile(List<File> source) {}
	default void afterCompile(List<Context> data, boolean mapped, PluginContext ctx) {}
}
