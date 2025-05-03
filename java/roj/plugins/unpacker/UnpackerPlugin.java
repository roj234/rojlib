package roj.plugins.unpacker;

import roj.collect.CollectionX;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.ui.Argument;
import roj.ui.CommandContext;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/5/31 0:26
 */
@SimplePlugin(id = "unpacker", version = "1.0", desc = """
	通用文件解包器
	[支持的文件格式]
	asar (Electron静态资源)
	har (F12工具保存的网页)
	mhtml (Ctrl+S保存的网页)
	scene (壁纸引擎壁纸包)
	
	[指令]
	unpack <文件> <输出目录> [格式] [模式]
	""")
public class UnpackerPlugin extends Plugin {
	@Override
	protected void onEnable() throws Exception {
		var type = CollectionX.toMap(Arrays.asList("asar", "har", "mhtml", "scene"));
		var mode = CollectionX.toMap(Arrays.asList("list"));

		registerCommand(literal("unpack").then(
			argument("in", Argument.file()).executes(this::autoUnpack).then(
				argument("out", Argument.folder()).executes(this::autoUnpack).then(
					argument("packType", Argument.oneOf(type)).executes(this::unpack).then(
						argument("mode", Argument.oneOf(mode)).executes(ctx -> getLogger().warn("未实现"))
		)))));
	}

	private void autoUnpack(CommandContext ctx) {
		File file = ctx.argument("in", File.class);
		String ext = IOUtil.extensionName(file.getName());
		if (ext.equals("har") || ext.equals("asar") || ext.equals("mhtml")) {
			ctx.put("packType", ext);
		} else if (ext.equals("pkg")) {
			ctx.put("packType", "scene");
		} else {
			getLogger().error("无法确定文件{}的类型，请手动指定", file);
			return;
		}

		if (ctx.argument("out", File.class) == null) {
			ctx.put("out", file.getParentFile());
		}
		unpack(ctx);
	}

	private void unpack(CommandContext ctx) {
		File in = ctx.argument("in", File.class);
		File out = ctx.argument("out", File.class);

		Unpacker unpacker = switch (ctx.argument("packType", String.class)) {
			case "asar" -> new Asar();
			case "har" -> new Har();
			case "mhtml" -> new MHtml();
			case "scene" -> new Scene();
			default -> throw OperationDone.NEVER;
		};

		try {
			unpacker.load(in);
			unpacker.export(out, "");
		} catch (IOException e) {
			getLogger().error("解包失败", e);
		} finally {
			IOUtil.closeSilently(unpacker);
		}
	}
}