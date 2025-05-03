package roj.plugins.minecraft.diff;

import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.plugins.KeyStorePlugin;
import roj.ui.Argument;
import roj.ui.Terminal;
import roj.util.Helpers;

import java.io.File;
import java.security.KeyPair;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/16 4:53
 */
@SimplePlugin(id = "mcdiff", desc = """
	为Minecraft存档设计的支持数字签名的差异文件格式
	
	[高压更新包]
	前置为keyStore插件
	先用它加载或创建一个EdDSA证书
	然后用mcdiff init <证书名称>或mcdiff initNoSign初始化
	之后使用mcdiff <压缩包(老状态)> <源文件夹(新状态)> <差异输出>
	
	[存档压缩&备份]
	压缩regions,entities和poi: chunktrim 世界文件夹
	创建压缩的备份: chunktrim backup 世界文件夹 备份文件夹
	""",
	version = "2.0", depend = "keyStore")
public class McDiffPlugin extends Plugin {
	private McDiffServer server;

	@Override
	protected void onEnable() throws Exception {
		var ks = (KeyStorePlugin) getPluginManager().getPlugin("keyStore").instance();

		File file1 = new File(getDataFolder(), "ignore.json");
		ChunkTrim.init(file1);

		registerCommand(literal("mcdiff")
			.then(literal("patch")
				.then(argument("差异文件", Argument.file())
					.then(argument("源文件夹", Argument.folder())
						.executes(ctx -> {

			var diff = new McDiffClient();
			diff.load(ctx.argument("差异文件", File.class), true);
			diff.apply(ctx.argument("源文件夹", File.class));
		})))).then(literal("init")
				.then(argument("证书名称", ks.asKeyPair())
					.executes(ctx -> {

			server = new McDiffServer(ctx.argument("证书名称", KeyPair.class));
		}))).then(literal("initNoSign")
				.executes(ctx -> {

			server = new McDiffServer(null);
		})).then(argument("压缩包", Argument.file())
				.then(argument("源文件夹", Argument.folder())
					.then(argument("差异输出", Argument.fileOptional(true))
						.executes(ctx -> {

			if (server == null) {
				getLogger().error("请先用mcdiff init初始化证书");
				return;
			}

			server.makeDiff(
				ctx.argument("压缩包", File.class),
				ctx.argument("源文件夹", File.class),
				Helpers.alwaysTrue(),
				ctx.argument("差异输出", File.class)
			);
		})))));

		registerCommand(literal("chunktrim").then(argument("chunk", Argument.folder()).executes(ctx -> {
			char c = Terminal.readChar("yn", "您正原位替换世界，确认吗？");
			if (c != 'y') return;
			System.out.println("您确认了操作.");
			ChunkTrim.createInline(ctx.argument("chunk", File.class));
		}).then(argument("backup", Argument.fileOptional(false)).executes(ctx -> {
			var file = ctx.argument("chunk", File.class);
			var backup = ctx.argument("backup", File.class);
			if (!backup.isDirectory() && !backup.mkdirs()) {
				Terminal.error("无法创建保存目录");
				return;
			}
			ChunkTrim.createBackup(file, backup);
		}))));

		registerCommand(literal("mcgroupby")
			.then(argument("chunk", Argument.folder())
			.then(argument("backup", Argument.fileOptional(false)).executes(ctx -> {
			var file = ctx.argument("chunk", File.class);
			var backup = ctx.argument("backup", File.class);
			if (!backup.isDirectory() && !backup.mkdirs()) {
				Terminal.error("无法创建保存目录");
				return;
			}
			GroupBy.createBackup(file, backup);
		}))));
	}
}