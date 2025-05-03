package roj.asmx;

import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/6 23:03
 */
public class ConstantPoolHooks implements Transformer {
	private final MyHashMap<Object, Object> reference = new MyHashMap<>(), declare = new MyHashMap<>(),
		annotationClass = new MyHashMap<>(), annotationField = new MyHashMap<>(), annotationMethod = new MyHashMap<>();

	public ConstantPoolHooks declaredClass(String owner, Hook<? super ClassNode> cb) { add(declare, owner, cb); return this; }
	public ConstantPoolHooks declaredMethod(String owner, String name, String desc, Hook<? super MethodNode> cb) { add(declare, new MemberDescriptor(owner, name, desc), cb); return this; }
	public ConstantPoolHooks declaredField(String owner, String name, String desc, Hook<? super FieldNode> cb) { add(declare, new MemberDescriptor(owner, name, desc), cb); return this; }
	public ConstantPoolHooks declaredAnyMethod(String name, String desc, Hook<? super MethodNode> cb) { add(declare, new MemberDescriptor("", name, desc), cb); return this; }
	public ConstantPoolHooks declaredAnyField(String name, String desc, Hook<? super FieldNode> cb) { add(declare, new MemberDescriptor("", name, desc), cb); return this; }
	public ConstantPoolHooks referencedMethod(String owner, String name, String desc, Hook<? super CstRef> cb) { add(reference, new MemberDescriptor(owner, name, desc), cb); return this; }
	public ConstantPoolHooks referencedField(String owner, String name, String desc, Hook<? super CstRef> cb) { add(reference, new MemberDescriptor(owner, name, desc), cb); return this; }
	public ConstantPoolHooks referencedAnyMethod(String name, String desc, Hook<? super CstRef> cb) { add(reference, new MemberDescriptor("", name, desc), cb); return this; }
	public ConstantPoolHooks referencedAnyField(String name, String desc, Hook<? super CstRef> cb) { add(reference, new MemberDescriptor("", name, desc), cb); return this; }
	public ConstantPoolHooks referencedClass(String name, Hook<? super CstClass> cb) { add(reference, name, cb); return this; }
	public ConstantPoolHooks annotatedClass(String type, Hook<? super ClassNode> tr) { add(annotationClass, type, tr); return this; }
	public ConstantPoolHooks annotatedField(String type, Hook<? super FieldNode> tr) { add(annotationField, type, tr); return this; }
	public ConstantPoolHooks annotatedMethod(String type, Hook<? super MethodNode> tr) { add(annotationMethod, type, tr); return this; }

	@SuppressWarnings("unchecked")
	private static void add(Map<Object, Object> reference, Object key, Object cb) {
		Object prev = reference.putIfAbsent(key, cb);
		if (prev != null) {
			if (prev instanceof Hook<?>) {
				if (cb instanceof Hook) reference.put(key, SimpleList.asModifiableList(prev, cb));
				else {
					reference.put(key, cb);
					((List<Object>) cb).add(0, prev);
				}
			} else {
				if (cb instanceof Hook) ((List<Object>) prev).add(cb);
				else ((List<Object>) prev).addAll(((List<?>) cb));
			}
		}
	}

	@Override
	public boolean transform(String name, Context ctx) throws TransformException {
		var d = ClassUtil.getInstance().sharedDesc;
		var data = ctx.getData();

		Object tr;
		boolean mod = false;

		var cpArr = data.cp.data();
		for (int i = 0; i < cpArr.size(); i++) {
			Constant c = cpArr.get(i);
			switch (c.type()) {
				case Constant.CLASS:
					tr = reference.get(((CstClass) c).name().str());
					if (tr != null) mod |= transform(tr, data, c);
				break;
				case Constant.FIELD:
				case Constant.METHOD:
				case Constant.INTERFACE:
					var ref = (CstRef) c;
					d.owner = ref.owner();
					d.name = ref.name();
					d.rawDesc = ref.rawDesc();

					tr = reference.get(d);
					if (tr != null) mod |= transform(tr, data, c);

					d.owner = "";
					tr = reference.get(d);
					if (tr != null) mod |= transform(tr, data, c);
				break;
			}
		}

		mod |= checkAnnotation(data, data, Attribute.ClAnnotations, annotationClass);
		mod |= checkAnnotation(data, data, Attribute.RtAnnotations, annotationClass);

		tr = declare.get(data.name());
		if (tr != null) mod |= transform(tr, data, data);

		mod |= invokeDeclare(data, data.methods, d, annotationMethod);
		mod |= invokeDeclare(data, data.fields, d, annotationField);
		return mod;
	}
	private boolean invokeDeclare(ClassNode data, SimpleList<? extends Member> nodes, MemberDescriptor d, MyHashMap<Object, Object> ref) throws TransformException {
		boolean mod = false;
		Object tr;

		for (int i = 0; i < nodes.size(); i++) {
			Member node = nodes.get(i);

			mod |= checkAnnotation(data, node, Attribute.ClAnnotations, ref);
			mod |= checkAnnotation(data, node, Attribute.RtAnnotations, ref);

			d.owner = data.name();
			d.name = node.name();
			d.rawDesc = node.rawDesc();

			tr = declare.get(d);
			if (tr != null) mod |= transform(tr, data, node);

			d.owner = "";
			tr = declare.get(d);
			if (tr != null) mod |= transform(tr, data, node);
		}
		return mod;
	}
	private boolean checkAnnotation(ClassNode data, Attributed node, TypedKey<Annotations> flag, MyHashMap<Object, Object> ref) throws TransformException {
		Annotations attr = node.getAttribute(data.cp, flag);
		boolean mod = false;
		if (attr != null) {
			List<Annotation> annotations = attr.annotations;
			for (int i = 0; i < annotations.size(); i++) {
				Object o = ref.get(annotations.get(i).type());
				if (o != null) mod |= transform(o, data, node);
			}
		}
		return mod;
	}

	@SuppressWarnings("unchecked")
	private boolean transform(Object tr, ClassNode data, Object ctx) throws TransformException {
		if (tr instanceof Hook<?> trx) return trx.transform(data, Helpers.cast(ctx));

		List<Hook<?>> list = (List<Hook<?>>) tr;
		boolean mod = false;
		for (int i = 0; i < list.size(); i++) mod |= list.get(i).transform(data, Helpers.cast(ctx));
		return mod;
	}

	public interface Hook<T> {
		boolean transform(ClassNode context, T node) throws TransformException;
	}
}