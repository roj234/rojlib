package roj.compiler.plugins.annotations;

import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;

/**
 * @author Roj234
 * @since 2024/12/1 0001 8:40
 */
@LavaPlugin(name = "annotations", desc = "Lava语言的一些注解")
public class AnnotationsPlugin {
	public static void pluginInit(LavaApi api) {
		api.addAnnotationProcessor(new ClassStage());
		api.addAnnotationProcessor(new CompileStage());
	}
}
