package roj.plugin;

import roj.RojLib;
import roj.asm.Opcodes;
import roj.asmx.AnnotationRepo;
import roj.asmx.Context;
import roj.asmx.Transformer;
import roj.asmx.launcher.Loader;
import roj.asmx.launcher.Tweaker;
import roj.config.ConfigMaster;
import roj.config.node.MapValue;
import roj.event.EventTransformer;
import roj.text.ParseException;
import roj.text.TextUtil;
import roj.ui.Tty;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/12/31 1:20
 */
public final class PanTweaker extends Tweaker implements Transformer {
	static MapValue CONFIG;
	static AnnotationRepo annotations;

	@Override
	public void init(List<String> args, Loader loader) {
		File file = new File("plugins/Core/config.yml");
		try {
			CONFIG = file.isFile() ? ConfigMaster.YAML.parse(file).asMap() : new MapValue();
		} catch (IOException | ParseException e) {
			Helpers.athrow(e);
		}

		super.init(args, loader);

		loader.registerTransformer(EventTransformer.register(Tweaker.CONDITIONAL));
		loader.registerTransformer(this);

		try {
			annotations = loader.getAnnotations();
		} catch (IOException e) {
			throw new IllegalStateException("无法读取注解数据", e);
		}

		if (TextUtil.consoleAbsent) {
			System.err.println("[警告]控制台不存在，请使用Web终端输入内容，否则可能造成不可预知的问题");
			var fallback = new Tty.NoAnsi();
			RojLib.setObj("roj.ui.Terminal.fallback", fallback);
			fallback.start();
		}
	}

	@Override
	public boolean transform(String name, Context ctx) {
		if (name.equals("roj/plugin/PluginDescriptor") || name.equals("roj/plugin/PanSecurityManager")) {
			var data = ctx.getData();
			SecureRandom rnd = new SecureRandom();
			for (int i = rnd.nextInt(16) + 8; i >= 0; i--) data.newField(Opcodes.ACC_PRIVATE, Integer.toString(rnd.nextInt(), 36), "Ljava/lang/Object;");
			for (int i = rnd.nextInt(16) + 8; i >= 0; i--) data.newField(Opcodes.ACC_PRIVATE, Integer.toString(rnd.nextInt(), 36), "B");
			for (int i = 0; i < 16; i++) data.newField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC, Integer.toString(rnd.nextInt(), 36), "Ljava/lang/Object;");
			ArrayUtil.shuffle(data.fields, rnd);
			return true;
		}
		return false;
	}
}