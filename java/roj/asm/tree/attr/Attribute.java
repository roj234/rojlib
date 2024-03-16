package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.type.Signature;
import roj.asm.visitor.XAttrCode;
import roj.collect.IntBiMap;
import roj.util.AttributeKey;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/26 23:26
 */
public abstract class Attribute {
	public static final IntBiMap<String> NAMED_ID = new IntBiMap<>();
	public static <T> AttributeKey<T> createAttributeName(String name) {
		NAMED_ID.putInt(NAMED_ID.size(), name);
		return new AttributeKey<>(name);
	}

	public static final AttributeKey<TypeAnnotations>
		RtTypeAnnotations = createAttributeName("RuntimeVisibleTypeAnnotations"),
		ClTypeAnnotations = createAttributeName("RuntimeInvisibleTypeAnnotations");
	public static final AttributeKey<Annotations>
		RtAnnotations = createAttributeName("RuntimeVisibleAnnotations"),
		ClAnnotations = createAttributeName("RuntimeInvisibleAnnotations");
	public static final AttributeKey<ParameterAnnotations>
		RtParameterAnnotations = createAttributeName("RuntimeVisibleParameterAnnotations"),
		ClParameterAnnotations = createAttributeName("RuntimeInvisibleParameterAnnotations");
	public static final AttributeKey<Signature> SIGNATURE = createAttributeName("Signature");
	// class
	public static final AttributeKey<AttrRecord> Record = createAttributeName("Record");
	public static final AttributeKey<InnerClasses> InnerClasses = createAttributeName("InnerClasses");
	public static final AttributeKey<AttrModule> Module = createAttributeName("Module");
	public static final AttributeKey<AttrClassList> ModulePackages = createAttributeName("ModulePackages");
	public static final AttributeKey<AttrString> ModuleMainClass = createAttributeName("ModuleMainClass");
	public static final AttributeKey<AttrString> NestHost = createAttributeName("NestHost");
	public static final AttributeKey<AttrClassList> PermittedSubclasses = createAttributeName("PermittedSubclasses");
	public static final AttributeKey<AttrClassList> NestMembers = createAttributeName("NestMembers");
	public static final AttributeKey<AttrString> SourceFile = createAttributeName("SourceFile");
	public static final AttributeKey<BootstrapMethods> BootstrapMethods = createAttributeName("BootstrapMethods");
	public static final AttributeKey<EnclosingMethod> EnclosingMethod = createAttributeName("EnclosingMethod");
	// method
	public static final AttributeKey<XAttrCode> Code = createAttributeName("Code");
	public static final AttributeKey<MethodParameters> MethodParameters = createAttributeName("MethodParameters");
	public static final AttributeKey<AttrClassList> Exceptions = createAttributeName("Exceptions");
	public static final AttributeKey<AnnotationDefault> AnnotationDefault = createAttributeName("AnnotationDefault");
	// field
	public static final AttributeKey<ConstantValue> ConstantValue = createAttributeName("ConstantValue");

	public abstract String name();

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId(name())).putInt(0);
		int i = w.wIndex();
		toByteArrayNoHeader(w, pool);
		w.putInt(i-4, w.wIndex()-i);
	}
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) { w.put(getRawData()); }

	public boolean isEmpty() { return false; }

	public abstract String toString();

	public DynByteBuf getRawData() { throw new UnsupportedOperationException(getClass().getName()); }

}