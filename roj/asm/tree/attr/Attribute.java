package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.type.Signature;
import roj.asm.visitor.XAttrCode;
import roj.collect.IntBiMap;
import roj.util.DynByteBuf;
import roj.util.TypedName;

/**
 * @author Roj234
 * @since 2021/5/26 23:26
 */
public abstract class Attribute {
	public static final IntBiMap<String> NAMED_ID = new IntBiMap<>();
	public static <T> TypedName<T> createAttributeName(String name) {
		NAMED_ID.putInt(NAMED_ID.size(), name);
		return new TypedName<>(name);
	}

	public static final TypedName<TypeAnnotations>
		RtTypeAnnotations = createAttributeName("RuntimeVisibleTypeAnnotations"),
		ClTypeAnnotations = createAttributeName("RuntimeInvisibleTypeAnnotations");
	public static final TypedName<Annotations>
		RtAnnotations = createAttributeName("RuntimeVisibleAnnotations"),
		ClAnnotations = createAttributeName("RuntimeInvisibleAnnotations");
	public static final TypedName<ParameterAnnotations>
		RtParameterAnnotations = createAttributeName("RuntimeVisibleParameterAnnotations"),
		ClParameterAnnotations = createAttributeName("RuntimeInvisibleParameterAnnotations");
	public static final TypedName<Signature> SIGNATURE = createAttributeName("Signature");
	// class
	public static final TypedName<AttrRecord> Record = createAttributeName("Record");
	public static final TypedName<InnerClasses> InnerClasses = createAttributeName("InnerClasses");
	public static final TypedName<AttrModule> Module = createAttributeName("Module");
	public static final TypedName<AttrClassList> ModulePackages = createAttributeName("ModulePackages");
	public static final TypedName<AttrString> ModuleMainClass = createAttributeName("ModuleMainClass");
	public static final TypedName<AttrString> NestHost = createAttributeName("NestHost");
	public static final TypedName<AttrClassList> PermittedSubclasses = createAttributeName("PermittedSubclasses");
	public static final TypedName<AttrClassList> NestMembers = createAttributeName("NestMembers");
	public static final TypedName<AttrString> SourceFile = createAttributeName("SourceFile");
	public static final TypedName<BootstrapMethods> BootstrapMethods = createAttributeName("BootstrapMethods");
	public static final TypedName<EnclosingMethod> EnclosingMethod = createAttributeName("EnclosingMethod");
	// method
	public static final TypedName<XAttrCode> Code = createAttributeName("Code");
	public static final TypedName<MethodParameters> MethodParameters = createAttributeName("MethodParameters");
	public static final TypedName<AttrClassList> Exceptions = createAttributeName("Exceptions");
	public static final TypedName<AnnotationDefault> AnnotationDefault = createAttributeName("AnnotationDefault");
	// field
	public static final TypedName<ConstantValue> ConstantValue = createAttributeName("ConstantValue");

	public abstract String name();

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId(name())).putInt(0);
		int i = w.wIndex();
		toByteArray1(w, pool);
		w.putInt(i-4, w.wIndex()-i);
	}
	protected void toByteArray1(DynByteBuf w, ConstantPool cp) { w.put(getRawData()); }

	public boolean isEmpty() { return false; }

	public abstract String toString();

	public DynByteBuf getRawData() { throw new UnsupportedOperationException(getClass().getName()); }

}