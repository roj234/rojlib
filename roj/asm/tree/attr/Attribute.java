package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.type.Signature;
import roj.util.DynByteBuf;
import roj.util.TypedName;

/**
 * @author Roj234
 * @since 2021/5/26 23:26
 */
public abstract class Attribute {
	public static final TypedName<TypeAnnotations>
		RtTypeAnnotations = new TypedName<>("RuntimeVisibleTypeAnnotations"),
		ClTypeAnnotations = new TypedName<>("RuntimeInvisibleTypeAnnotations");

	public static final TypedName<Annotations>
		RtAnnotations = new TypedName<>("RuntimeVisibleAnnotations"),
		ClAnnotations = new TypedName<>("RuntimeInvisibleAnnotations");

	public static final TypedName<ParameterAnnotations>
		RtParameterAnnotations = new TypedName<>("RuntimeVisibleParameterAnnotations"),
		ClParameterAnnotations = new TypedName<>("RuntimeInvisibleParameterAnnotations");

	public static final TypedName<Signature> SIGNATURE = new TypedName<>("Signature");

	// class
	public static final TypedName<AttrRecord> Record = new TypedName<>("Record");
	public static final TypedName<InnerClasses> InnerClasses = new TypedName<>("InnerClasses");
	public static final TypedName<AttrModule> Module = new TypedName<>("Module");
	public static final TypedName<AttrModulePackages> ModulePackages = new TypedName<>("ModulePackages");
	public static final TypedName<AttrClassRef> ModuleMainClass = new TypedName<>("ModuleMainClass");
	public static final TypedName<AttrClassRef> NestHost = new TypedName<>("NestHost");
	public static final TypedName<AttrStringList> PermittedSubclasses = new TypedName<>("PermittedSubclasses");
	public static final TypedName<AttrStringList> NestMembers = new TypedName<>("NestMembers");
	public static final TypedName<AttrUTF> SourceFile = new TypedName<>("SourceFile");
	public static final TypedName<BootstrapMethods> BootstrapMethods = new TypedName<>("BootstrapMethods");
	public static final TypedName<EnclosingMethod> EnclosingMethod = new TypedName<>("EnclosingMethod");

	// method
	public static final TypedName<AttrCode> Code = new TypedName<>("Code");
	public static final TypedName<MethodParameters> MethodParameters = new TypedName<>("MethodParameters");
	public static final TypedName<AttrStringList> Exceptions = new TypedName<>("Exceptions");
	public static final TypedName<AnnotationDefault> AnnotationDefault = new TypedName<>("AnnotationDefault");

	// field
	public static final TypedName<ConstantValue> ConstantValue = new TypedName<>("ConstantValue");



	protected Attribute(String name) { this.name = name; }
	public final String name;

	public final void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getUtfId(name)).putInt(0);
		int i = w.wIndex();
		toByteArray1(w, pool);
		w.putInt(i - 4, w.wIndex() - i);
	}
	protected void toByteArray1(DynByteBuf w, ConstantPool cp) {}

	public boolean isEmpty() { return false; }

	public abstract String toString();

	public DynByteBuf getRawData() { throw new UnsupportedOperationException(getClass().getName()); }
}