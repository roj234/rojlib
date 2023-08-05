package roj.mildwind.asm;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrString;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.collect.ToIntMap;
import roj.reflect.ClassDefiner;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2020/9/28 12:44
 */
public final class JsWriter {
	public static final boolean DEBUG = Boolean.getBoolean("kscript.debug");

	final ConstantData data;

	public JsFunctionCompiled compile() {
		if (syncMethod != null) syncMethod.one(RETURN);

		data.dump();
		ConstantData parse = Parser.parse(Parser.toByteArrayShared(data));
		System.out.println(parse);
		Class<?> klass = ClassDefiner.INSTANCE.defineClassC(data);
		klass.getMethods();
		return null;
	}

	public JsWriter(String file) {
		data = new ConstantData();
		data.name("roj/mildwind/asm/AST000");
		data.parent("roj/mildwind/asm/JsFunctionCompiled");
		data.putAttr(new AttrString("SourceFile", file));
		data.newField(AccessFlag.PRIVATE, "methodId", "I");
	}

	private CodeWriter syncMethod;
	private int syncId;
	private final ToIntMap<Object> syncRef = new ToIntMap<>();

	public int sync(Object t) {
		int i = syncRef.getOrDefault(t, -1);
		if (i >= 0) return i;

		String type = TypeHelper.class2asm(t.getClass());
		int fieldId = data.newField(AccessFlag.PRIVATE|AccessFlag.STATIC, "sync`"+syncId+"`", type);
		if (syncMethod == null) {
			int sync = data.getMethod("sync");
			if (sync >= 0) syncMethod = ((AttrCodeWriter) data.methods.get(sync).attrByName("Code")).cw;
			else {
				syncMethod = data.newMethod(AccessFlag.PUBLIC|AccessFlag.FINAL, "sync", "([Ljava/lang/Object;)V");
				syncMethod.visitSize(33, 22);
				// todo add RETURN on end
			}
		}

		syncRef.putInt(t, fieldId);

		syncMethod.one(ALOAD_1);
		syncMethod.ldc(syncId);
		syncMethod.one(AALOAD);
		syncMethod.clazz(CHECKCAST, type);
		syncMethod.field(PUTSTATIC, data, fieldId);

		syncId++;
		return fieldId;
	}
}
