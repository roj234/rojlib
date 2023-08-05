package roj.asm.util;

import roj.asm.cst.ConstantPool;
import roj.asm.misc.ReflectClass;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.tree.attr.ParameterAnnotations;
import roj.asm.visitor.XAttrCode;

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
		if ((clz.modifier() & AccessFlag.ANNOTATION) == 0) return null;

		if (clz instanceof ReflectClass) {
			Class<?> o = ((ReflectClass) clz).owner;
			Modifiable mod = new Modifiable();
			Retention r = o.getAnnotation(Retention.class);
			if (r != null) {
				switch (r.value()) {
					case CLASS: mod.kind = CLASS; break;
					case SOURCE: mod.kind = SOURCE; break;
					case RUNTIME: mod.kind = RUNTIME; break;
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
			switch (a.type) {
				case "java/lang/annotation/Retention":
					if (!a.containsKey("value")) return null;
					switch (a.getEnumValue("value", "RUNTIME")) {
						case "SOURCE":  mod.kind = SOURCE; break;
						case "CLASS":   mod.kind = CLASS;  break;
						case "RUNTIME": mod.kind = RUNTIME;break;
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
							case "TYPE":tmp |= TYPE;break;
							case "FIELD":tmp |= FIELD;break;
							case "METHOD":tmp |= METHOD;break;
							case "PARAMETER":tmp |= PARAMETER;break;
							case "CONSTRUCTOR":tmp |= CONSTRUCTOR;break;
							case "LOCAL_VARIABLE":tmp |= LOCAL_VARIABLE;break;
							case "ANNOTATION_TYPE":tmp |= ANNOTATION_TYPE;break;
							case "PACKAGE":tmp |= PACKAGE;break;
							case "TYPE_PARAMETER":tmp |= TYPE_PARAMETER;break;
							case "TYPE_USE":tmp |= TYPE_USE;break;
						}
					}
					mod.applicableTo = tmp;
					break;
			}
		}
		return mod;
	}

	public static XAttrCode getOrCreateCode(ConstantPool cp, MethodNode node) {
		XAttrCode a = node.parsedAttr(cp, Attribute.Code);
		if (a != null) return a;
		node.putAttr(a = new XAttrCode());
		return a;
	}

	public static List<Annotation> getAnnotations(ConstantPool cp, Attributed node, boolean vis) {
		Annotations a = node.parsedAttr(cp, vis?Attribute.RtAnnotations:Attribute.ClAnnotations);
		return a == null ? null : a.annotations;
	}

	public static Annotation getAnnotation(List<Annotation> list, String name) {
		if (list == null) return null;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).type.equals(name)) return list.get(i);
		}
		return null;
	}

	public static List<InnerClasses.InnerClass> getInnerClasses(ConstantPool cp, IClass node) {
		InnerClasses ic = node.parsedAttr(cp, Attribute.InnerClasses);
		return ic == null ? null : ic.classes;
	}

	public static List<List<Annotation>> getParameterAnnotation(ConstantPool cp, MethodNode m, boolean vis) {
		ParameterAnnotations pa = m.parsedAttr(cp, vis ? Attribute.RtParameterAnnotations : Attribute.ClParameterAnnotations);
		return pa == null ? null : pa.annotations;
	}
}
