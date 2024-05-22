package roj.compiler.plugins.annotations;

import roj.asm.Opcodes;
import roj.asm.tree.Attributed;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.RawNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.type.TypeHelper;
import roj.collect.MyHashSet;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.api.Processor;
import roj.util.Helpers;

import java.util.Set;

/**
 * @author Roj234
 * @since 2024/6/10 0010 3:27
 */
public final class AnnotationProcessor2 implements Processor {
	@Override
	public boolean acceptClasspath() {return true;}

	private static final Set<String> ACCEPTS = new MyHashSet<>("roj/compiler/plugins/annotations/Attach", "roj/compiler/plugins/annotations/Operator");
	@Override
	public Set<String> acceptedAnnotations() {return ACCEPTS;}

	@Override
	public void handle(LocalContext ctx, IClass file, Attributed node, Annotation annotation) {
		var gctx = ctx.classes;

		String type = annotation.type();
		if (type.endsWith("Attach")) {
			if (file == node) {
				if (annotation.getString("value") != null)
					ctx.report(Kind.SEVERE_WARNING, "plugins.annotation.namedAttachOnType", file);
				for (RawNode mn : file.methods()) attach(mn, gctx, annotation);
			} else {
				attach((RawNode) node, gctx, annotation);
			}
		} else if (type.endsWith("Operator")) {
			String token = annotation.getString("value");
			int flag = annotation.getInt("flag");
		}
	}

	private static void attach(RawNode mn, GlobalContext gctx, Annotation annotation) {
		if ((mn.modifier()&Opcodes.ACC_STATIC) == 0) return;
		String desc = mn.rawDesc();
		if (!desc.startsWith("(L")) return;

		var params = TypeHelper.parseMethod(desc);
		IClass info = gctx.getClassInfo(params.get(0).owner);
		if (info != null) {
			params.remove(0);

			desc = TypeHelper.getMethod(params);
			if (info.getMethod(mn.name(), desc) >= 0) return;

			MethodNode replace = new MethodNode(Opcodes.ACC_PUBLIC, info.name(), annotation.getString("value", mn.name()), desc);
			replace.putAttr(new AttachedMethod(new MethodNode(mn)));
			info.methods().add(Helpers.cast(replace));

			gctx.invalidateResolveHelper(info);
		}
	}
}