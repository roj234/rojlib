package roj.asmx;

import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.ParameterAnnotations;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/1/6 23:03
 */
public class ConstantPoolHooks implements Transformer {
	private final HashMap<Object, Object> reference = new HashMap<>(), declare = new HashMap<>();
	private final HashMap<String, Object>
		annotationClass = new HashMap<>(), annotationField = new HashMap<>(), annotationMethod = new HashMap<>(),
		annotationParameters = new HashMap<>();
	private boolean isNotEmpty;

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
	public ConstantPoolHooks annotatedParameter(String type, Hook<? super MethodNode> tr) { add(annotationParameters, type, tr); return this; }

	public boolean isNotEmpty() {
		if (isNotEmpty) return true;
		return isNotEmpty = (reference.size()|declare.size()|annotationClass.size()|annotationField.size()|annotationMethod.size()|annotationParameters.size()) != 0;
	}

	@SuppressWarnings("unchecked")
	private static <T> void add(Map<T, Object> reference, T key, Object cb) {
		Object prev = reference.putIfAbsent(key, cb);
		if (prev != null) {
			if (prev instanceof Hook<?>) {
				if (cb instanceof Hook) reference.put(key, ArrayList.asModifiableList(prev, cb));
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

		if (!reference.isEmpty()) {
			var cpArr = data.cp.constants();
			for (int i = 0; i < cpArr.size(); i++) {
				Constant c = cpArr.get(i);
				switch (c.type()) {
					case Constant.CLASS -> {
						tr = reference.get(((CstClass) c).value().str());
						if (tr != null) mod |= transform(tr, data, c);
					}
					case Constant.FIELD, Constant.METHOD, Constant.INTERFACE -> {
						var ref = (CstRef) c;
						d.owner = ref.owner();
						d.name = ref.name();
						d.rawDesc = ref.rawDesc();

						tr = reference.get(d);
						if (tr != null) mod |= transform(tr, data, c);

						d.owner = "";
						tr = reference.get(d);
						if (tr != null) mod |= transform(tr, data, c);
					}
				}
			}
		}

		if (!annotationClass.isEmpty()) {
			mod |= checkAnnotation(data, data, Attribute.InvisibleAnnotations, annotationClass);
			mod |= checkAnnotation(data, data, Attribute.VisibleAnnotations, annotationClass);
		}
		if (!annotationParameters.isEmpty()) {
			mod |= checkParameterAnnotation(data);
		}

		tr = declare.get(data.name());
		if (tr != null) mod |= transform(tr, data, data);

		mod |= invokeDeclare(data, data.methods, d, annotationMethod);
		mod |= invokeDeclare(data, data.fields, d, annotationField);
		return mod;
	}
	private boolean checkParameterAnnotation(ClassNode data) throws TransformException {
		Set<String> classes = Collections.emptySet();
		boolean mod = false;

		var nodes = data.methods;
		for (int i = 0; i < nodes.size(); i++) {
			Member node = nodes.get(i);

			classes.clear();
			classes = checkParameterAnnotations(data, node, classes, Attribute.InvisibleParameterAnnotations);
			classes = checkParameterAnnotations(data, node, classes, Attribute.VisibleParameterAnnotations);

			for (String type : classes) {
				Object o = annotationParameters.get(type);
				if (o != null) mod |= transform(o, data, node);
			}
		}
		return mod;
	}

	private static Set<String> checkParameterAnnotations(ClassNode data, Member node, Set<String> classes, TypedKey<ParameterAnnotations> key) {
		ParameterAnnotations attribute = node.getAttribute(data.cp, key);
		if (attribute != null) {
			if (classes.isEmpty()) classes = new HashSet<>();

			for (List<Annotation> annotations : attribute.annotations) {
				for (Annotation annotation : annotations) {
					classes.add(annotation.type());
				}
			}
		}
		return classes;
	}

	private boolean invokeDeclare(ClassNode data, ArrayList<? extends Member> nodes, MemberDescriptor d, HashMap<String, Object> ref) throws TransformException {
		boolean mod = false;
		Object tr;

		for (int i = 0; i < nodes.size(); i++) {
			Member node = nodes.get(i);

			if (!ref.isEmpty()) {
				mod |= checkAnnotation(data, node, Attribute.InvisibleAnnotations, ref);
				mod |= checkAnnotation(data, node, Attribute.VisibleAnnotations, ref);
			}

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
	private boolean checkAnnotation(ClassNode data, Attributed node, TypedKey<Annotations> key, HashMap<String, Object> ref) throws TransformException {
		if (ref.isEmpty()) return false;

		Annotations attr = node.getAttribute(data.cp, key);
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