package roj.asm.util;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.misc.ReflectClass;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import static roj.asm.tree.AnnotationClass.*;

/**
 * @author Roj233
 * @since 2022/4/23 14:18
 */
public class AttrHelper {
	public static AnnotationClass getAnnotationInfo(IClass clz) {
		if ((clz.accessFlag() & AccessFlag.ANNOTATION) == 0) return null;

		if (clz instanceof ReflectClass) {
			Class<?> o = ((ReflectClass) clz).owner;
			Modifiable mod = new Modifiable();
			Retention r = o.getAnnotation(Retention.class);
			if (r != null) {
				switch (r.value()) {
					case CLASS:
						mod.kind = CLASS;
						break;
					case SOURCE:
						mod.kind = SOURCE;
						break;
					case RUNTIME:
						mod.kind = RUNTIME;
						break;
				}
			}

			Repeatable re = o.getAnnotation(Repeatable.class);
			if (re != null) mod.repeatOn = re.value().getName().replace('.', '/');

			Target t = o.getAnnotation(Target.class);
			if (t != null) {
				int v = 0;
				for (ElementType e : t.value()) {
					v |= 1 << e.ordinal();
				}
				mod.applicableTo = v;
			}

			return mod;
		} else {
			List<Annotation> list = getAnnotations(clz instanceof ConstantData ? ((ConstantData) clz).cp : null, clz, true);
			if (list != null) return getAnnotationInfo(list);
		}

		throw new UnsupportedOperationException(clz.getClass().getSimpleName());
	}

	public static AnnotationClass.Modifiable getAnnotationInfo(List<? extends Annotation> list) {
		Modifiable mod = new Modifiable();
		for (int i = 0; i < list.size(); i++) {
			Annotation a = list.get(i);
			switch (a.clazz) {
				case "java/lang/annotation/Retention":
					if (!a.containsKey("value")) return null;
					switch (a.getEnumValue("value", "RUNTIME")) {
						case "SOURCE":
							mod.kind = SOURCE;
							break;
						case "CLASS":
							mod.kind = CLASS;
							break;
						case "RUNTIME":
							mod.kind = RUNTIME;
							break;
					}
					break;
				case "java/lang/annotation/Repeatable":
					if (!a.containsKey("value")) return null;
					mod.repeatOn = a.getClass("value").owner;
					break;
				case "java/lang/annotation/Target":
					int tmp = 0;
					if (!a.containsKey("value")) return null;
					List<AnnVal> array = a.getArray("value");
					for (int j = 0; j < array.size(); j++) {
						switch (array.get(j).asEnum().value) {
							case "TYPE":
								tmp |= TYPE;
								break;
							case "FIELD":
								tmp |= FIELD;
								break;
							case "METHOD":
								tmp |= METHOD;
								break;
							case "PARAMETER":
								tmp |= PARAMETER;
								break;
							case "CONSTRUCTOR":
								tmp |= CONSTRUCTOR;
								break;
							case "LOCAL_VARIABLE":
								tmp |= LOCAL_VARIABLE;
								break;
							case "ANNOTATION_TYPE":
								tmp |= ANNOTATION_TYPE;
								break;
							case "PACKAGE":
								tmp |= PACKAGE;
								break;
							case "TYPE_PARAMETER":
								tmp |= TYPE_PARAMETER;
								break;
							case "TYPE_USE":
								tmp |= TYPE_USE;
								break;
						}
					}
					mod.applicableTo = tmp;
					break;
			}
		}
		return mod;
	}

	public static AttrCode getOrCreateCode(ConstantPool cp, MethodNode node) {
		if (node instanceof Method) {
			Method m = (Method) node;
			if (m.code != null) {return m.code;} else {
				return m.code = new AttrCode(m);
			}
		} else {
			Attribute attr = node.attrByName("Code");
			if (attr == null) {
				AttrCode code = new AttrCode(node);
				node.attributes().putByName(code);
				return code;
			} else if (attr instanceof AttrCode) {
				return (AttrCode) attr;
			} else {
				AttrCode code = new AttrCode(node, Parser.reader(attr), cp);
				node.attributes().putByName(code);
				return code;
			}
		}
	}

	public static List<Annotation> getAnnotations(ConstantPool cp, Attributed node, boolean vis) {
		Attribute a = node.attrByName(vis ? Annotations.VISIBLE : Annotations.INVISIBLE);
		if (a == null) return null;
		if (a instanceof Annotations) return ((Annotations) a).annotations;
		Annotations anno = new Annotations(vis ? Annotations.VISIBLE : Annotations.INVISIBLE, Parser.reader(a), cp);
		node.attributes().putByName(anno);
		return anno.annotations;
	}

	public static Annotation getAnnotation(List<Annotation> list, String name) {
		if (list == null) return null;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).clazz.equals(name)) return list.get(i);
		}
		return null;
	}

	public static AnnotationDefault getAnnotationDef(ConstantPool cp, MethodNode node) {
		Attribute a = node.attrByName(AnnotationDefault.NAME);
		if (a == null) return null;
		if (a instanceof AnnotationDefault) return (AnnotationDefault) a;
		AnnotationDefault anno = new AnnotationDefault(Parser.reader(a), cp);
		node.attributes().putByName(anno);
		return anno;
	}

	public static List<InnerClasses.InnerClass> getInnerClasses(ConstantPool cp, IClass node) {
		Attribute a = node.attrByName("InnerClasses");
		if (a == null) return null;

		InnerClasses ic;
		if (a instanceof InnerClasses) ic = (InnerClasses) a;
		else node.attributes().putByName(ic = new InnerClasses(Parser.reader(a), cp));
		return ic.classes;
	}

	public static BootstrapMethods getBootstrapMethods(ConstantPool cp, IClass node) {
		Attribute a = node.attrByName("BootstrapMethods");
		if (a == null) return null;

		BootstrapMethods bm;
		if (a instanceof BootstrapMethods) bm = (BootstrapMethods) a;
		else node.attributes().putByName(bm = new BootstrapMethods(Parser.reader(a), cp));
		return bm;
	}

	public static Signature getSignature(ConstantPool cp, Attributed node) {
		Attribute attr = node.attrByName("Signature");
		if (attr == null) return null;

		if (attr instanceof Signature) return (Signature) attr;
		//else if (attr instanceof AttrUTF) attr = Signature.parse(((AttrUTF) attr).value);
		else attr = Signature.parse(((CstUTF) cp.get(attr.getRawData())).getString());
		node.putAttr(attr);
		return (Signature) attr;
	}

	public static List<List<Annotation>> getParameterAnnotation(ConstantPool cp, MethodNode m, boolean vis) {
		Attribute a = m.attrByName(vis ? ParameterAnnotations.VISIBLE : ParameterAnnotations.INVISIBLE);
		if (a == null) return null;
		if (a instanceof ParameterAnnotations) return ((ParameterAnnotations) a).annotations;

		ParameterAnnotations pa = new ParameterAnnotations(vis ? ParameterAnnotations.VISIBLE : ParameterAnnotations.INVISIBLE, Parser.reader(a), cp);
		m.putAttr(pa);
		return pa.annotations;
	}
}
