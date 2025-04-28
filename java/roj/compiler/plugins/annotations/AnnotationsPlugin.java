package roj.compiler.plugins.annotations;

import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;

/**
 * @author Roj234
 * @since 2024/12/1 0001 8:40
 */
@LavaPlugin(name = "annotations", desc = """
		注解处理测试插件

		@Attach: 将这个类中所有的静态函数附加到它们第一个参数指定的类上
		@AutoIncrement: 以指定的start和step为它覆盖的每个字段赋整数值
		@Getter/Setter: 自动生成getter和setter
		@Operator: 注册表达式重载
		@Property: 假装getter和setter是一个属性
		@Singleton: 比双检锁更好的单例模式""")
public class AnnotationsPlugin {
	public static void pluginInit(LavaApi api) {
		api.addAnnotationProcessor(new ClassStage());
		api.addAnnotationProcessor(new CompileStage());
	}
}
