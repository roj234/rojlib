package roj.compiler.resolve;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.ClassDefinition;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.annotation.Annotation;
import roj.asm.attr.AnnotationDefault;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.config.data.CEntry;
import roj.config.serial.CVisitor;

import java.util.List;

/**
 * 一个{@link java.lang.annotation.Annotation 注解}类的ASM元数据
 * @see roj.compiler.resolve.ResolveHelper
 */
public class AnnotationType {
	/**
	 * 可堆叠注解是否存在
	 * @see roj.compiler.api.Stackable
	 */
	public boolean stackable;
	/**
	 * Repeatable注解是否存在，如果存在，那么是容器类型
	 * @see java.lang.annotation.Repeatable
	 */
	public String repeatOn = null;

	private int applicableTo = -1;

	public static final int
			TYPE = 1 << 2, FIELD = 1 << 3, METHOD = 1 << 4, PARAMETER = 1 << 5, CONSTRUCTOR = 1 << 6, LOCAL_VARIABLE = 1 << 7, ANNOTATION_TYPE = 1 << 8, PACKAGE = 1 << 9,
			// 1.8
			TYPE_PARAMETER = 1 << 10, TYPE_USE = 1 << 11, MODULE = 1 << 12, RECORD_COMPONENT = 1 << 13;

	/**
	 * 注解允许应用到的目标，由多个位组合而成，可能由Target注解控制，默认为任何
	 * @see java.lang.annotation.Target
	 */
	@MagicConstant(flags = {TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE, TYPE_PARAMETER, TYPE_USE, MODULE, RECORD_COMPONENT})
	public int applicableTo() { return applicableTo; }

	public static final int SOURCE = 1, CLASS = 3, RUNTIME = 2;
	/**
	 * 可获取该注解的阶段，值是这三者之一{@link #SOURCE}, {@link #CLASS}, {@link #RUNTIME}
	 * @see java.lang.annotation.Retention
	 */
	@MagicConstant(intValues = {SOURCE, CLASS, RUNTIME})
	public int retention() { return applicableTo & 3; }

	public MyHashMap<String, CEntry> elementDefault = new MyHashMap<>();
	public MyHashMap<String, Type> elementType = new MyHashMap<>();

	public AnnotationType(ClassDefinition node) {
		if ((node.modifier()&Opcodes.ACC_ANNOTATION) == 0) throw new IllegalArgumentException(node.name()+"不是注解");

		var methods = node.methods();
		for (int i = 0; i < methods.size(); i++) {
			var method = (MethodNode)methods.get(i);
			if ((method.modifier() & Opcodes.ACC_STATIC) != 0) continue;
			var defVal = method.getAttribute(node.cp(), Attribute.AnnotationDefault);
			if (defVal != null)
				elementDefault.put(method.name(), defVal.val == null ? new LazyValue(defVal) : defVal.val);
			elementType.put(method.name(), method.returnType());
		}

		Annotations attr = node.getAttribute(node.cp(), Attribute.RtAnnotations);
		if (attr == null) return;

		List<Annotation> list = attr.annotations;
		int tmp = 0;

		for (int i = 0; i < list.size(); i++) {
			Annotation a = list.get(i);
			switch (a.type()) {
				case "java/lang/annotation/Retention" -> {
					if (!a.containsKey("value")) throw new NullPointerException("Invalid @Retention");
					tmp |= switch (a.getEnumValue("value", "RUNTIME")) {
						case "SOURCE" -> SOURCE;
						case "CLASS" -> CLASS;
						case "RUNTIME" -> RUNTIME;
						default -> throw new IllegalStateException("Unexpected Retention: "+a.getEnumValue("value", "RUNTIME"));
					};
				}
				case "java/lang/annotation/Repeatable" -> {
					if (!a.containsKey("value")) throw new NullPointerException("Invalid @Repeatable");
					repeatOn = a.getClass("value").owner;
				}
				case "java/lang/annotation/Target" -> {
					if (!a.containsKey("value")) throw new NullPointerException("Invalid @Target");
					var list1 = a.getList("value");
					for (int j = 0; j < list1.size(); j++) {
						tmp |= switch (list1.getEnumValue(j)) {
							case "TYPE" -> TYPE;
							case "FIELD" -> FIELD;
							case "METHOD" -> METHOD;
							case "PARAMETER" -> PARAMETER;
							case "CONSTRUCTOR" -> CONSTRUCTOR;
							case "LOCAL_VARIABLE" -> LOCAL_VARIABLE;
							case "ANNOTATION_TYPE" -> ANNOTATION_TYPE;
							case "PACKAGE" -> PACKAGE;
							case "TYPE_PARAMETER" -> TYPE_PARAMETER;
							case "TYPE_USE" -> TYPE_USE;
							case "MODULE" -> MODULE;
							case "RECORD_COMPONENT" -> RECORD_COMPONENT;
							default -> 0;
						};
					}
				}
				case "roj/compiler/api/Stackable" -> stackable = true;
			}
		}

		applicableTo = tmp == 0 ? -1 : tmp;
	}

	/**
	 * @since 2024/6/21 17:39
	 */
	static final class LazyValue extends CEntry {
		public final AnnotationDefault ref;

		public LazyValue(AnnotationDefault ref) {this.ref = ref;}

		@Override public roj.config.data.Type getType() {return ref.val.getType();}
		@Override public char dataType() {return ref.val.dataType();}
		@Override public void accept(CVisitor visitor) {ref.val.accept(visitor);}
		@Override public Object raw() {return ref.val.raw();}
		@Override public String toString() {return ref.val.toString();}
	}
}