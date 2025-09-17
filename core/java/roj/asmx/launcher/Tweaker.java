package roj.asmx.launcher;

import org.jetbrains.annotations.NotNull;
import roj.RojLib;
import roj.asm.ClassNode;
import roj.asm.annotation.AList;
import roj.asm.annotation.Annotation;
import roj.asmx.AnnotatedElement;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.TransformUtil;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 处理Autoload注解
 * @author Roj234
 * @since 2024/5/2 4:09
 */
public class Tweaker {
	public static final CodeWeaver NIXIM = new CodeWeaver();
	public static final ConstantPoolHooks CONDITIONAL = new ConstantPoolHooks();

	private record A(String name, int priority) {}

	// for override
	protected void init(List<String> args, Loader loader) {
		try {
			List<A> classes = new ArrayList<>();

			for (AnnotatedElement element : loader.getAnnotations().annotatedBy("roj/asmx/launcher/Autoload")) {
				Annotation a = element.annotations().get("roj/asmx/launcher/Autoload");
				int priority = a.getInt("priority", 0);
				String owner = element.owner();

				var intrinsic = a.getInt("intrinsic", -1);
				if (intrinsic >= 0 && (!RojLib.fastJni() || !RojLib.hasNative(intrinsic))) continue;

				switch (a.getEnumValue("value", "INIT")) {
					case "INIT" -> classes.add(new A(owner, priority));
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
			Function<String, Boolean> existenceChecker = resource -> Loader.instance.getResource(resource) != null;

			CONDITIONAL.annotatedMethod("roj/asmx/launcher/Conditional", (context, node) -> {
				var annotation = Annotation.findInvisible(context.cp, node, "roj/asmx/launcher/Conditional");
				if (!existence.computeIfAbsent(getConditionalTarget(annotation), existenceChecker)) {
					String action = annotation.getEnumValue("action", "REMOVE");
					switch (action) {
						case "REMOVE" -> context.methods.remove(node);
						case "DUMMY" -> TransformUtil.apiOnly(context, node);
					}
					return true;
				}
				return false;
			});
			CONDITIONAL.annotatedClass("roj/asmx/launcher/Conditional", (context, node) -> {
				var annotation = Annotation.findInvisible(context.cp, node, "roj/asmx/launcher/Conditional");
				if (!existence.computeIfAbsent(getConditionalTarget(annotation), existenceChecker)) {
					AList itf = annotation.getList("itf");
					for (int i = 0; i < itf.size(); i++) {
						context.interfaces().remove(itf.getType(i).owner());
					}

					String action = annotation.getEnumValue("action", "REMOVE");
					if (action.equals("DUMMY")) {
						TransformUtil.apiOnly(context);
					}
					return true;
				}
				return false;
			});

			if (classes.size() != 0) {
				classes.sort((l, r) -> Integer.compare(r.priority, l.priority));
				for (A a : classes) {
					Class.forName(a.name.replace('/', '.'), true, Tweaker.class.getClassLoader());
				}
			}

			loader.registerTransformer(NIXIM);
			loader.registerTransformer(CONDITIONAL);
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@NotNull
	private static String getConditionalTarget(Annotation annotation) {
		return annotation.containsKey("target") ? annotation.getClass("target").owner().concat(".class") : annotation.getString("value");
	}
}