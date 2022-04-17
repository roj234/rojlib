package roj.dev;

import roj.asm.Parser;
import roj.asm.tree.AccessData;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.AttrCode;
import roj.asm.type.ParamHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.NodeHelper;
import roj.collect.MyHashSet;
import roj.util.Helpers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/2/21 15:08
 */
public class HRAgent extends Thread {
    private static boolean loaded;
    private static Instrumentation instInst;
    private static boolean rojLib;

    public static boolean isLoaded() {
        return loaded;
    }

    public static void useRojLib() {
        rojLib = true;
    }

    // 限制, 只能使用java的类, 不能使用rojlib的类
    // 因为‘我编译我自己’
    // 只允许本地连接, 因为数据明文传输
    public static void premain(String agentArgs, Instrumentation inst) {
        if (inst != null) instInst = inst;
        else inst = instInst;

        if (!inst.isRedefineClassesSupported()) {
            System.err.println("[HR] VM不允许类的重定义");
            return;
        }
        new HRAgent(agentArgs == null || agentArgs.isEmpty() ? HRRemote.DEFAULT_PORT : Integer.parseInt(agentArgs))
                .start();
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    private final int port;

    HRAgent(int port) {
        this.port = port;
        setName("HRAgent");
        setDaemon(true);
    }

    @Override
    public void run() {
        loaded = true;
        System.out.println("[HR] Agent已启动");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            HashMap<String, Class<?>> byName = new HashMap<>();

            while (in.read() == 0x66) {
                socket.setSoTimeout(1000);
                int count = in.readUnsignedShort();
                int found = 0;

                ClassDefinition[] toTransform = new ClassDefinition[count];

                for (Class<?> clazz : instInst.getAllLoadedClasses())
                    byName.put(clazz.getName(), clazz);

                while (count-- > 0) {
                    String name = in.readUTF();
                    int dataLen = in.readInt();

                    Class<?> t = byName.get(name);
                    boolean mod = t != null && instInst.isModifiableClass(t);
                    s:
                    if (mod) {
                        byte[] clazz = new byte[dataLen];
                        in.readFully(clazz, 0, dataLen);
                        if (rojLib) {
                            try {
                                clazz = RIN.fixRemoving(t, clazz);
                            } catch (Throwable e) {
                                e.printStackTrace();
                                break s;
                            }
                        }
                        toTransform[found++] = new ClassDefinition(t, clazz);
                    } else {
                        if (in.skipBytes(dataLen) < dataLen) throw new EOFException();
                        if (t != null) {
                            System.err.println("[HR] 此类无法更新: " + name);
                        }
                    }
                }

                if (found > 0) {
                    if (toTransform.length != found) toTransform = Arrays.copyOf(toTransform, found);
                    try {
                        instInst.redefineClasses(toTransform);
                        System.out.println("[HR] 应用 " + found + " 跳过 " + (toTransform.length - found));

                        out.write(HRRemote.R_OK);
                        out.writeShort(found);
                    } catch (Throwable e) {
                        System.err.println("[HR] " + e.getMessage());

                        out.write(HRRemote.R_ERR);
                        out.writeShort(0);
                    }
                }
                socket.setSoTimeout(0);
            }
        } catch (Throwable e) {
            System.err.println("[HR] Agent断开连接 " + e.getMessage());
        }
        loaded = false;
    }

    // 因为不能带着自己调试自己啊, 所以要手动开启
    // 解决不能删除方法的问题
    static class RIN {
        static byte[] fixRemoving(Class<?> clazz, byte[] buf) {
            AccessData data = Parser.parseAccessDirect(buf);

            MyHashSet<Object> removed = new MyHashSet<>();

            Field[] fields = clazz.getDeclaredFields();
            List<AccessData.MOF> fields1 = data.fields;
            find:
            for (Field f : fields) {
                String desc = ParamHelper.class2asm(f.getType());
                for (int j = 0; j < fields1.size(); j++) {
                    AccessData.MOF f1 = fields1.get(j);
                    if (f1.desc.equals(desc) && f1.name.equals(f.getName())) {
                        fields1.remove(j);
                        continue find;
                    }
                }
                removed.add(f);
            }
            if (!fields1.isEmpty())
                throw new IllegalStateException("[HR] 有字段被添加: " + fields1);

            Method[] methods = clazz.getDeclaredMethods();
            List<AccessData.MOF> methods1 = data.fields;
            find:
            for (Method m : methods) {
                String desc = ParamHelper.class2asm(m.getParameterTypes(), m.getReturnType());
                for (int j = 0; j < methods1.size(); j++) {
                    AccessData.MOF m1 = methods1.get(j);
                    if (m1.desc.equals(desc) && m1.name.equals(m.getName())) {
                        methods1.remove(j);
                        continue find;
                    }
                }
                removed.add(m);
            }
            for (int i = methods1.size() - 1; i >= 0; i--) {
                AccessData.MOF m = methods1.get(i);
                if ((m.acc & (AccessFlag.STATIC | AccessFlag.FINAL)) != 0 && (m.acc & AccessFlag.PRIVATE) != 0) {
                    methods1.remove(i);
                }
            }
            if (!methods1.isEmpty())
                throw new IllegalStateException("[HR] 有方法被添加: " + methods1);

            if (removed.isEmpty()) return buf;

            // transform
            ConstantData cd = Parser.parseConstants(buf);
            for (Object o : removed) {
                if (o instanceof Field) {
                    Field f = (Field) o;
                    roj.asm.tree.Field myField = new roj.asm.tree.Field(f);
                    cd.fields.add(Helpers.cast(myField));
                } else {
                    Method m = (Method) o;
                    String desc = ParamHelper.class2asm(m.getParameterTypes(), m.getReturnType());
                    roj.asm.tree.Method myMethod = new roj.asm.tree.Method(m);
                    cd.methods.add(Helpers.cast(myMethod));
                    if ((myMethod.accesses & (AccessFlag.ABSTRACT | AccessFlag.NATIVE)) == 0) {
                        AttrCode code = myMethod.code = new AttrCode(myMethod);
                        code.localSize = (char) ParamHelper.paramSize(myMethod.rawDesc());
                        code.instructions.add(NodeHelper.X_RETURN(myMethod.getReturnType().nativeName()));
                    }
                }
            }
            return Parser.toByteArray(cd);
        }
    }
}
