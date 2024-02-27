package roj.compiler.plugins.annotations;

import roj.asm.Opcodes;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.collect.MyHashSet;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.api.Processor;
import roj.compiler.resolve.FieldBridge;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/6/10 0010 3:27
 */
public final class AnnotationProcessor2 implements Processor {
	@Override
	public boolean acceptClasspath() {return true;}

	private static final Set<String> ACCEPTS = new MyHashSet<>("roj/compiler/plugins/annotations/Attach", "roj/compiler/plugins/annotations/Operator", "roj/compiler/plugins/annotations/Property");
	@Override
	public Set<String> acceptedAnnotations() {return ACCEPTS;}

	@Override
	public void handle(LocalContext ctx, IClass file, Attributed node, Annotation annotation) {
		var gctx = ctx.classes;

		String type = annotation.type();
		if (type.endsWith("Attach")) {
			if (file == node) {
				if (annotation.getString("value") != null)
					ctx.report(Kind.ERROR, "plugins.annotation.namedAttachOnType", file);
				for (RawNode mn : file.methods()) attach(mn, gctx, annotation);
			} else {
				attach((RawNode) node, gctx, annotation);
			}
		} else if (type.endsWith("Operator")) {
			String token = annotation.getString("value");
			int flag = annotation.getInt("flag");
		} else if (type.endsWith("Property")) {
			var name = annotation.getString("value");
			if (file.getField(name) >= 0) {
				ctx.report(Kind.SEVERE_WARNING, "plugins.annotation.propertyExist", name);
				return;
			}

			var getter = annotation.getString("getter");
			if (getter == null) getter = accessorName("get", name);
			var setter = annotation.getString("setter");
			if (setter == null) setter = accessorName("set", name);

			int readId = file.getMethod(getter);
			if (readId < 0) {
				ctx.report(Kind.ERROR, "plugins.annotation.getterNotFound", name, getter);
				return;
			}

			int writeId = setter == null ? -1 : file.getMethod(setter);

			var getterImpl = (MethodNode)file.methods().get(readId);
			if (!getterImpl.rawDesc().startsWith("()")) {
				ctx.report(Kind.ERROR, "plugins.annotation.argError.getter", name, getter);
				return;
			}

			if (writeId >= 0) {
				var setterImpl = (MethodNode)file.methods().get(writeId);
				if (!setterImpl.rawDesc().endsWith(")V") || setterImpl.parameters().size() != 1) {
					ctx.report(Kind.ERROR, "plugins.annotation.argError.setter", name, getter);
					return;
				}
			}

			var fn = new FieldNode(Opcodes.ACC_PUBLIC | (getterImpl.modifier()&Opcodes.ACC_STATIC) | (writeId < 0 ? Opcodes.ACC_FINAL : 0), name, getterImpl.returnType());

			var sign = getterImpl.parsedAttr(file.cp(), Attribute.SIGNATURE);
			if (sign != null) {
				var returnType = sign.values.get(sign.values.size()-1);
				if (returnType.genericType() != 0) {
					sign = new Signature(Signature.FIELD);
					sign.values = Collections.singletonList(returnType);
					fn.putAttr(sign);
				}
			}

			fn.putAttr(new FieldBridge(file, readId, writeId));
			file.fields().add(Helpers.cast(fn));
			if (file instanceof CompileUnit cu) cu.noStore(fn);
		}
	}

	private static String accessorName(String prefix, String field) {
		var s = new CharList().append(prefix).append(field);
		int i = prefix.length();
		s.set(i, Character.toUpperCase(s.charAt(i)));
		return s.toStringAndFree();
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