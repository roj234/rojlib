package roj.asmx.launcher;

import roj.RojLib;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.annotation.Annotation;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asmx.AnnotatedElement;
import roj.asmx.NodeFilter;
import roj.asmx.nixim.NiximException;
import roj.asmx.nixim.NiximSystemV2;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.reflect.litasm.Intrinsics;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * 处理Autoload注解
 * @author Roj234
 * @since 2024/5/2 0002 4:09
 */
public class DefaultTweaker implements ITweaker {
	public static final NiximSystemV2 NIXIM = new NiximSystemV2();
	public static final NodeFilter CONDITIONAL = new NodeFilter();

	private record A(String name, int priority) {}

	@Override
	public void init(List<String> args, Bootstrap loader) {
		try {
			List<A> classes = new SimpleList<>(), transformers = new SimpleList<>();

			for (AnnotatedElement element : loader.getAnnotations().annotatedBy("roj/asmx/launcher/Autoload")) {
				Annotation a = element.annotations().get("roj/asmx/launcher/Autoload");
				int priority = a.getInt("priority", 0);
				String owner = element.owner();

				var intrinsic = a.getInt("intrinsic", -1);
				if (intrinsic >= 0 && (!Intrinsics.available() || !RojLib.hasNative(intrinsic))) continue;

				switch (a.getEnumValue("value", null)) {
					case "INIT" -> classes.add(new A(owner, priority));
					case "TRANSFORMER" -> transformers.add(new A(owner, priority));
					case "NIXIM" -> {
						try {
							NIXIM.load(Parser.parseConstants(IOUtil.read(loader.getResource(owner.concat(".class")))));
						} catch (NiximException e) {
							Helpers.athrow(e);
						}
					}
				}
			}

			if ((classes.size()|transformers.size()) != 0) {
				Comparator<A> cmp = (l, r) -> Integer.compare(r.priority, l.priority);
				classes.sort(cmp);
				transformers.sort(cmp);

				ClassNode autoloader = new ClassNode();
				autoloader.name("roj/asmx/autoload/Autoloader");

				CodeWriter w = autoloader.newMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", "()V");
				w.visitSize(3, 0);
				for (A d : classes) {
					w.ldc(new CstClass(d.name));
					w.invokeS("roj/reflect/ReflectionUtils", "ensureClassInitialized", "(Ljava/lang/Class;)V");
				}
				for (A d : transformers) {
					w.field(Opcodes.GETSTATIC, "roj/asmx/launcher/Bootstrap", "instance", "Lroj/asmx/launcher/Bootstrap;");
					w.newObject(d.name);
					w.invokeV("roj/asmx/launcher/Bootstrap", "registerTransformer", "(Lroj/asmx/ITransformer;)V");
				}
				w.one(Opcodes.RETURN);

				Class<?> klass = ReflectionUtils.defineWeakClass(Parser.toByteArrayShared(autoloader));
				ReflectionUtils.ensureClassInitialized(klass);
			}

			loader.registerTransformer(NIXIM);
			loader.registerTransformer(CONDITIONAL);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}