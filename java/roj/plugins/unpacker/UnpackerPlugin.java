package roj.plugins.unpacker;

import roj.collect.CollectionX;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.platform.Plugin;
import roj.platform.SimplePlugin;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandContext;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/5/31 0031 0:26
 */
@SimplePlugin(id = "unpacker", version = "1.0", desc = "解包几种常见的文件格式")
public class UnpackerPlugin extends Plugin {
	@Override
	protected void onEnable() throws Exception {
		var type = CollectionX.toMap(Arrays.asList("asar", "har", "mhtml", "scene"));
		var mode = CollectionX.toMap(Arrays.asList("list"));

		registerCommand(literal("unpack").then(
			argument("in", Argument.file()).executes(this::autoUnpack).then(
				argument("out", Argument.file(true)).executes(this::autoUnpack).then(
					argument("packType", Argument.anyOf(type)).executes(this::unpack).then(
						argument("mode", Argument.anyOf(mode)).executes(ctx -> getLogger().warn("未实现"))
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