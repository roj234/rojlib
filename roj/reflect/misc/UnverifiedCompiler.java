package roj.reflect.misc;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.constant.CstLong;
import roj.asm.struct.Clazz;
import roj.asm.struct.Method;
import roj.asm.struct.attr.AttrCode;
import roj.asm.struct.insn.FieldInsnNode;
import roj.asm.struct.insn.LoadConstInsnNode;
import roj.asm.struct.insn.NPInsnNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnList;
import roj.asm.util.type.NativeType;
import roj.asm.util.type.Type;
import roj.io.IOUtil;
import roj.reflect.DirectMethodAccess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/1 15:06
 */
public final class UnverifiedCompiler {
    public static void main(String[] args) throws IOException {
        createFastNumCast(args[0]);
        createUnsafe(args[0]);
        createUncheckedCast(args[0]);

        /*System.out.println("Test: ");
        int i = 233333;
        System.out.println("233333 as float " + FastNumCast.toFloat(i));
        System.out.println("233333 as double " + FastNumCast.toDouble(i));

        Integer in = 23333;
        System.out.println("header " + Runtime.OBJECT_HEADER_SIZE);
        System.out.println("arch " + Runtime.OS_ARCH);
        long addr = Unsafe.objectAddress(in);
        System.out.println("int address " + addr);
        long offset = -1;
        try {
            offset = J8Util.objectFieldOffset(Integer.class.getDeclaredField("value"));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        System.out.println("int offset " + offset);
        System.out.println("get I 23333 as int " + UncheckedCast.int32(addr + offset));*/


    }

    private static void createUncheckedCast(String arg) throws IOException {
        InsnList common = new InsnList();
        common.add(NPInsnNode.of(Opcodes.LLOAD_0));
        common.add(new FieldInsnNode(Opcodes.GETSTATIC, "roj/reflect/misc/Runtime", "OBJECT_HEADER_SIZE", new Type(NativeType.LONG)));
        common.add(NPInsnNode.of(Opcodes.LSUB));
        common.add(NPInsnNode.of(Opcodes.LSTORE_0));
        common.add(NPInsnNode.of(Opcodes.ALOAD_1));
        // stack = 4
        // local = 2

        Clazz clz = new Clazz(52 << 16, AccessFlag.PUBLIC | AccessFlag.SUPER_OR_SYNC | AccessFlag.FINAL,
                "roj/reflect/misc/UncheckedCast", "java/lang/Object");
        clz.interfaces.add(DirectMethodAccess.MAGIC_ACCESSOR_CLASS);

        Method method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "ptr", "(J)Ljava/lang/Object;");
        AttrCode code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "roj/reflect/misc/VHolder", "value", new Type("java/lang/Object")));
        code.instructions.add(NPInsnNode.of(Opcodes.ARETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint8", "(J)B");
        code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "java/lang/Byte", "value", new Type(NativeType.BYTE)));
        code.instructions.add(NPInsnNode.of(Opcodes.IRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint16", "(J)S");
        code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "java/lang/Short", "value", new Type(NativeType.SHORT)));
        code.instructions.add(NPInsnNode.of(Opcodes.IRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint32", "(J)I");
        code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "java/lang/Integer", "value", new Type(NativeType.INT)));
        code.instructions.add(NPInsnNode.of(Opcodes.IRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint64", "(J)J");
        code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "java/lang/Long", "value", new Type(NativeType.LONG)));
        code.instructions.add(NPInsnNode.of(Opcodes.IRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "ptr", "(JLjava/lang/Object;)V");
        code = method.code = new AttrCode(method);
        code.localSize = 3;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(NPInsnNode.of(Opcodes.ALOAD_2));
        code.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "roj/reflect/misc/VHolder", "value", new Type("java/lang/Object")));
        code.instructions.add(NPInsnNode.of(Opcodes.RETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint8", "(JB)V");
        code = method.code = new AttrCode(method);
        code.localSize = 3;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(NPInsnNode.of(Opcodes.ILOAD_2));
        code.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "java/lang/Byte", "value", new Type(NativeType.BYTE)));
        code.instructions.add(NPInsnNode.of(Opcodes.RETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint16", "(JS)V");
        code = method.code = new AttrCode(method);
        code.localSize = 3;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(NPInsnNode.of(Opcodes.ILOAD_2));
        code.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "java/lang/Short", "value", new Type(NativeType.SHORT)));
        code.instructions.add(NPInsnNode.of(Opcodes.RETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint32", "(JI)V");
        code = method.code = new AttrCode(method);
        code.localSize = 3;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(NPInsnNode.of(Opcodes.ILOAD_2));
        code.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "java/lang/Integer", "value", new Type(NativeType.INT)));
        code.instructions.add(NPInsnNode.of(Opcodes.RETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "uint64", "(JJ)V");
        code = method.code = new AttrCode(method);
        code.localSize = 4;
        code.stackSize = 4;
        code.instructions.addAll(common);
        code.instructions.add(NPInsnNode.of(Opcodes.LLOAD_2));
        code.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "java/lang/Long", "value", new Type(NativeType.LONG)));
        code.instructions.add(NPInsnNode.of(Opcodes.RETURN));
        code.markEnd();
        clz.methods.add(method);

        try (FileOutputStream fos = new FileOutputStream(new File(arg + "/roj/reflect/misc/UncheckedCast.class"))) {
            clz.getBytes().writeToStream(fos);
        }
    }

    private static void createUnsafe(String arg) throws IOException {
        final File file = new File(arg + "/roj/reflect/misc/Unsafe.class");
        Clazz clz = Parser.parse(IOUtil.readFile(file), 0);

        if (clz.methods.size() < 4) return;

        clz.interfaces.add(DirectMethodAccess.MAGIC_ACCESSOR_CLASS);


        clz.methods.remove(0);

        Method method = clz.methods.get(1); // n32addr
        AttrCode code = method.code;
        code.localSize = 1;
        code.stackSize = 4;
        code.instructions.clear();
        code.instructions.add(NPInsnNode.of(Opcodes.ILOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.I2L));
        code.instructions.add(new LoadConstInsnNode(Opcodes.LDC2_W, new CstLong(0xFFFFFFFF)));
        code.instructions.add(NPInsnNode.of(Opcodes.LAND));
        code.instructions.add(NPInsnNode.of(Opcodes.LRETURN));
        code.markEnd();

        method = clz.methods.get(2); // n64addr
        code = method.code;
        code.localSize = 2;
        code.stackSize = 2;
        code.instructions.clear();
        code.instructions.add(NPInsnNode.of(Opcodes.ALOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.ASTORE_1));
        code.instructions.add(NPInsnNode.of(Opcodes.LLOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.LRETURN));
        code.markEnd();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            clz.getBytes().writeToStream(fos);
        }
    }

    private static void createFastNumCast(String arg) throws IOException {
        Clazz clz = new Clazz(52 << 16, AccessFlag.PUBLIC | AccessFlag.SUPER_OR_SYNC | AccessFlag.FINAL,
                "roj/reflect/misc/FastNumCast", "java/lang/Object");
        clz.interfaces = Collections.singletonList(DirectMethodAccess.MAGIC_ACCESSOR_CLASS);

        Method method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "toDouble", "(J)D");
        AttrCode code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 2;
        code.instructions.add(NPInsnNode.of(Opcodes.DLOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.DRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "toFloat", "(I)F");
        code = method.code = new AttrCode(method);
        code.localSize = 1;
        code.stackSize = 1;
        code.instructions.add(NPInsnNode.of(Opcodes.FLOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.FRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "toInteger", "(F)I");
        code = method.code = new AttrCode(method);
        code.localSize = 1;
        code.stackSize = 1;
        code.instructions.add(NPInsnNode.of(Opcodes.ILOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.IRETURN));
        code.markEnd();
        clz.methods.add(method);

        method = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, clz, "toLong", "(D)J");
        code = method.code = new AttrCode(method);
        code.localSize = 2;
        code.stackSize = 2;
        code.instructions.add(NPInsnNode.of(Opcodes.LLOAD_0));
        code.instructions.add(NPInsnNode.of(Opcodes.LRETURN));
        code.markEnd();
        clz.methods.add(method);

        try (FileOutputStream fos = new FileOutputStream(new File(arg + "/roj/reflect/misc/FastNumCast.class"))) {
            clz.getBytes().writeToStream(fos);
        }
    }
}
