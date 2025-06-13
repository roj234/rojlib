package roj.plugin;

import roj.RojLib;
import roj.asm.Opcodes;
import roj.asmx.AnnotationRepo;
import roj.asmx.Context;
import roj.asmx.Transformer;
import roj.asmx.event.EventTransformer;
import roj.asmx.launcher.Bootstrap;
import roj.asmx.launcher.DefaultTweaker;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.ui.NativeVT;
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
public final class PanTweaker extends DefaultTweaker implements Transformer {
	static CMap CONFIG;
	static AnnotationRepo annotations;

	@Override
	public void init(List<String> args, Bootstrap loader) {
		File file = new File("plugins/Core/config.yml");
		try {
			CONFIG = file.isFile() ? ConfigMaster.YAML.parse(file).asMap() : new CMap();
		} catch (IOException | ParseException e) {
			Helpers.athrow(e);
		}

		super.init(args, loader);

		loader.registerTransformer(EventTransformer.register(DefaultTweaker.CONDITIONAL));
		loader.registerTransformer(this);

		try {
			annotations = loader.getAnnotations();
		} catch (IOException e) {
			throw new IllegalStateException("无法读取注解数据", e);
		}

		if (NativeVT.getInstance() == null && !Boolean.getBoolean("roj.noAnsi")) {
			System.err.println("[警告]NativeVT不可用，请使用Web终端输入内容，否则可能造成不可预知的问题");
			var fallback = new NativeVT.Fallback();
			RojLib.BLACKBOARD.put("roj.ui.Terminal.fallback", fallback);
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