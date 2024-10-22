package roj.plugin;

import roj.RojLib;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.util.Context;
import roj.asmx.AnnotationRepo;
import roj.asmx.ITransformer;
import roj.asmx.event.EventTransformer;
import roj.asmx.launcher.ClassWrapper;
import roj.asmx.launcher.DefaultTweaker;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.reflect.litasm.Intrinsics;
import roj.text.CharList;
import roj.ui.ITerminal;
import roj.ui.NativeVT;
import roj.ui.Terminal;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/12/31 0031 1:20
 */
public final class PanTweaker extends DefaultTweaker implements ITransformer {
	static CMap CONFIG;
	static AnnotationRepo annotations;

	@Override
	public void init(List<String> args, ClassWrapper loader) {
		File file = new File("plugins/Core/config.yml");
		try {
			CONFIG = file.isFile() ? ConfigMaster.YAML.parse(file).asMap() : new CMap();
		} catch (IOException | ParseException e) {
			Helpers.athrow(e);
		}

		AnnotationRepo repo;
		loadAnnotations:
		try {
			File cache = null;
			if (CONFIG.getBool("annotation_cache")) {
				cache = new File("plugins/Core/annotation.bin");
				long length = cache.length();
				if (length > 8 && length < 10485760 && cache.lastModified() >= loader.getAnnotationTimestamp()) {
					try (var in = new FileInputStream(cache)) {
						repo = new AnnotationRepo();
						if (repo.deserialize(IOUtil.getSharedByteBuf().readStreamFully(in))) {
							loader.setAnnotations(repo);
							break loadAnnotations;
						}
					}
				}
			}
			repo = loader.getAnnotations();
			if (cache != null) {
				try (var out = new ByteList.ToStream(new FileOutputStream(cache))) {
					repo.serialize(out);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("无法读取注解数据", e);
		}

		super.init(args, loader);

		loader.registerTransformer(EventTransformer.register(DefaultTweaker.CONDITIONAL));
		loader.registerTransformer(this);
		if (Intrinsics.available()) {
			try {
				if (RojLib.hasNative(RojLib.AES_NI)) {
					DefaultTweaker.NIXIM.load(Parser.parseConstants(IOUtil.getResource("roj/crypt/n/AES.class")));
				}
			} catch (Exception e) {
				System.err.println("无法初始化FastJNI的AOP注入");
				e.printStackTrace();
			}
		}

		annotations = repo;

		if (NativeVT.getInstance() == null) {
			var sout = System.out;
			System.err.println("[警告]NativeVT不可用，请使用Web终端输入内容，否则可能造成不可预知的问题");
			RojLib.DATA.put("roj.ui.Terminal.fallback", new ITerminal() {
				@Override public boolean readBack(boolean sync) {return false;}
				@Override public void write(CharSequence str) {
					CharList x = Terminal.stripAnsi(new CharList(str));
					sout.print(x.toStringAndFree());
				}
			});
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