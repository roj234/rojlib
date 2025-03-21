package roj.asm.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.insn.AttrCode;
import roj.asm.type.Signature;
import roj.collect.IntBiMap;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

/**
 * @author Roj234
 * @since 2021/5/26 23:26
 */
public abstract class Attribute {
	public static final IntBiMap<String> NAMED_ID = new IntBiMap<>();
	public static <T> TypedKey<T> createAttributeName(String name) {
		NAMED_ID.putInt(NAMED_ID.size(), name);
		return new TypedKey<>(name);
	}

	public static final TypedKey<TypeAnnotations>
		RtTypeAnnotations = createAttributeName("RuntimeVisibleTypeAnnotations"),
		ClTypeAnnotations = createAttributeName("RuntimeInvisibleTypeAnnotations");
	public static final TypedKey<Annotations>
		RtAnnotations = createAttributeName("RuntimeVisibleAnnotations"),
		ClAnnotations = createAttributeName("RuntimeInvisibleAnnotations");
	public static final TypedKey<ParameterAnnotations>
		RtParameterAnnotations = createAttributeName("RuntimeVisibleParameterAnnotations"),
		ClParameterAnnotations = createAttributeName("RuntimeInvisibleParameterAnnotations");
	public static final TypedKey<Signature> SIGNATURE = createAttributeName("Signature");
	// class
	public static final TypedKey<RecordAttribute> Record = createAttributeName("Record");
	public static final TypedKey<InnerClasses> InnerClasses = createAttributeName("InnerClasses");
	public static final TypedKey<ModuleAttribute> Module = createAttributeName("Module");
	public static final TypedKey<ClassListAttribute> ModulePackages = createAttributeName("ModulePackages");
	public static final TypedKey<StringAttribute> ModuleMainClass = createAttributeName("ModuleMainClass");
	public static final TypedKey<StringAttribute> NestHost = createAttributeName("NestHost");
	public static final TypedKey<ClassListAttribute> PermittedSubclasses = createAttributeName("PermittedSubclasses");
	public static final TypedKey<ClassListAttribute> NestMembers = createAttributeName("NestMembers");
	public static final TypedKey<StringAttribute> SourceFile = createAttributeName("SourceFile");
	public static final TypedKey<BootstrapMethods> BootstrapMethods = createAttributeName("BootstrapMethods");
	public static final TypedKey<EnclosingMethod> EnclosingMethod = createAttributeName("EnclosingMethod");
	public static final TypedKey<StringAttribute> ModuleTarget = createAttributeName("ModuleTarget");
	// method
	public static final TypedKey<AttrCode> Code = createAttributeName("Code");
	public static final TypedKey<MethodParameters> MethodParameters = createAttributeName("MethodParameters");
	public static final TypedKey<ClassListAttribute> Exceptions = createAttributeName("Exceptions");
	public static final TypedKey<AnnotationDefault> AnnotationDefault = createAttributeName("AnnotationDefault");
	// field
	public static final TypedKey<ConstantValue> ConstantValue = createAttributeName("ConstantValue");

	public abstract String name();

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId(name())).putInt(0);
		int i = w.wIndex();
		toByteArrayNoHeader(w, pool);
		w.putInt(i-4, w.wIndex()-i);
	}
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) { w.put(getRawData()); }

	public boolean writeIgnore() { return false; }

	public abstract String toString();

	public DynByteBuf getRawData() { throw new UnsupportedOperationException(getClass().getName()); }
}