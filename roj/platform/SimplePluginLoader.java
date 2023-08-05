package roj.platform;

import roj.asm.tree.anno.Annotation;
import roj.asmx.annorepo.AnnotationInfo;
import roj.asmx.annorepo.AnnotationRepo;
import roj.collect.MyHashMap;
import roj.math.Version;
import roj.ui.terminal.Argument;
import roj.util.Helpers;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/26 0026 13:37
 */
public class SimplePluginLoader extends Plugin {
	public static final AnnotationRepo REPO = new AnnotationRepo();
	private final Map<String, PluginDescriptor> simplePlugins = new MyHashMap<>();

	private int loadAnnotationPlugins(File jar) {
		REPO.add(jar);

		Set<AnnotationInfo> infos = REPO.annotatedBy("roj/platform/SimplePlugin");
		for (AnnotationInfo info : infos) {
			PluginDescriptor pd = new PluginDescriptor();
			pd.mainClass = info.owner().replace('/', '.');
			Annotation annotation = info.annotations().get("roj/platform/SimplePlugin");
			pd.fileName = "annotation:"+ jar.getName();
			pd.id = annotation.getString("id");
			pd.version = new Version(annotation.getString("version", "1"));
			pd.desc = annotation.getString("desc", "");
			//pd.depend = annotation.getArray("depend").stream().map(AnnVal::asString).collect(Collectors.toList());
			//pd.loadAfter = annotation.getArray("loadAfter").stream().map(AnnVal::asString).collect(Collectors.toList());
			//pd.loadBefore = annotation.getArray("loadBefore").stream().map(AnnVal::asString).collect(Collectors.toList());
			simplePlugins.put(pd.id, pd);
		}
		int v = infos.size();
		infos.clear();
		return v;
	}

	@Override
	protected void onEnable() throws Exception {
		loadAnnotationPlugins(Helpers.getJarByClass(SimplePluginLoader.class));
		DefaultPluginSystem.CMD.register(literal("spl_add").then(argument("file", Argument.file()).executes(ctx -> {
			getLogger().info("正在读取文件中的注解插件");
			int v = loadAnnotationPlugins(ctx.argument("file", File.class));
			getLogger().info("找到了{}个",v);
		})));
		DefaultPluginSystem.CMD.register(literal("spl_load").then(argument("id", Argument.setOf(simplePlugins, false)).executes(ctx -> {
			PluginDescriptor pd = ctx.argument("id", PluginDescriptor.class);

			getLogger().info("正在加载注解插件 {}", pd);
			try {
				getPluginManager().registerPlugin(pd, (Plugin) Class.forName(pd.mainClass).newInstance());
				getPluginManager().loadPlugins();
			} catch (Exception e) {
				getLogger().error("无法加载注解插件 {}", e, pd);
			}
		})));
	}

	@Override
	protected void onDisable() {
		DefaultPluginSystem.CMD.unregister("spl_add");
		DefaultPluginSystem.CMD.unregister("spl_load");
	}
}
