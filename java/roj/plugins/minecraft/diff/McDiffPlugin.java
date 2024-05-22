package roj.plugins.minecraft.diff;

import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.plugins.KeyStorePlugin;
import roj.ui.terminal.Argument;
import roj.util.Helpers;

import java.io.File;
import java.security.KeyPair;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/16 0016 4:53
 */
@SimplePlugin(id = "mcDiff", desc = """
	为Minecraft存档设计的差异文件格式，同时支持数字签名
	[patch]
	前置为keyStore插件
	先用它加载或创建一个EdDSA证书
	然后用mcdiff init <证书名称>初始化
	之后使用mcdiff <压缩包(老状态)> <源文件夹(新状态)> <差异输出>""",
	version = "1.1", depend = "keyStore")
public class McDiffPlugin extends Plugin {
	private McDiffServer server;

	@Override
	protected void onEnable() throws Exception {
		var ks = (KeyStorePlugin) getPluginManager().getPlugin("keyStore").getInstance();

		registerCommand(literal("mcdiff")
			.then(literal("patch")
				.then(argument("差异文件", Argument.file())
					.then(argument("源文件夹", Argument.folder())
						.executes(ctx -> {

			var diff = new McDiffClient();
			diff.load(ctx.argument("差异文件", File.class));
			diff.apply(ctx.argument("源文件夹", File.class));
		})))).then(literal("init")
				.then(argument("证书名称", ks.asKeyPair())
					.executes(ctx -> {

			server = new McDiffServer(ctx.argument("证书名称", KeyPair.class));
		}))).then(argument("压缩包", Argument.file())
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
	}
}