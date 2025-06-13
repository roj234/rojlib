package roj.asmx.launcher;

import roj.RojLib;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.annotation.AList;
import roj.asm.annotation.Annotation;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asmx.AnnotatedElement;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 处理Autoload注解
 * @author Roj234
 * @since 2024/5/2 4:09
 */
public class DefaultTweaker implements ITweaker {
	public static final CodeWeaver NIXIM = new CodeWeaver();
	public static final ConstantPoolHooks CONDITIONAL = new ConstantPoolHooks();

	private record A(String name, int priority) {}

	@Override
	public void init(List<String> args, Bootstrap loader) {
		try {
			List<A> classes = new ArrayList<>(), transformers = new ArrayList<>();

			for (AnnotatedElement element : loader.getAnnotations().annotatedBy("roj/asmx/launcher/Autoload")) {
				Annotation a = element.annotations().get("roj/asmx/launcher/Autoload");
				int priority = a.getInt("priority", 0);
				String owner = element.owner();

				var intrinsic = a.getInt("intrinsic", -1);
				if (intrinsic >= 0 && (!RojLib.fastJni() || !RojLib.hasNative(intrinsic))) continue;

				switch (a.getEnumValue("value", null)) {
					case "INIT" -> classes.add(new A(owner, priority));
					case "TRANSFORMER" -> transformers.add(new A(owner, priority));
					case "NIXIM" -> {
						try {
							NIXIM.load(ClassNode.parseSkeleton(IOUtil.read(loader.getResource(owner.concat(".class")))));
						} catch (WeaveException e) {
							Helpers.athrow(e);
						}
					}
				}
			}

			ConcurrentHashMap<String, Boolean> existence = new ConcurrentHashMap<>();
			Function<String, Boolean> existenceChecker = resource -> Bootstrap.instance.getResource(resource) != null;

			CONDITIONAL.annotatedMethod("roj/asmx/launcher/Conditional", (context, node) -> {
				var annotation = Annotation.findInvisible(context.cp, node, "roj/asmx/launcher/Conditional");
				if (!existence.computeIfAbsent(annotation.getString("value"), existenceChecker)) {
					context.methods.remove(node);
					return true;
				}
				return false;
			});
			CONDITIONAL.annotatedClass("roj/asmx/launcher/Conditional", (context, node) -> {
				var annotation = Annotation.findInvisible(context.cp, node, "roj/asmx/launcher/Conditional");
				if (!existence.computeIfAbsent(annotation.getString("value"), existenceChecker)) {
					AList itf = annotation.getList("itf");
					for (int i = 0; i < itf.size(); i++) {
						context.interfaces().remove(itf.getType(i).owner());
					}
					return true;
				}
				return false;
			});

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
					w.invokeS("roj/reflect/Reflection", "ensureClassInitialized", "(Ljava/lang/Class;)V");
				}
				for (A d : transformers) {
					w.field(Opcodes.GETSTATIC, "roj/asmx/launcher/Bootstrap", "instance", "Lroj/asmx/launcher/Bootstrap;");
					w.newObject(d.name);
					w.invokeV("roj/asmx/launcher/Bootstrap", "registerTransformer", "(Lroj/asmx/ITransformer;)V");
				}
				w.insn(Opcodes.RETURN);

				Class<?> klass = Reflection.defineWeakClass(AsmCache.toByteArrayShared(autoloader));
				Reflection.ensureClassInitialized(klass);
			}

			loader.registerTransformer(NIXIM);
			loader.registerTransformer(CONDITIONAL);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}