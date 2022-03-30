package roj.dev;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author Roj233
 * @since 2022/2/21 15:08
 */
public class HRAgent extends Thread {
    private static boolean loaded;
    private static Instrumentation instInst;

    public static boolean isLoaded() {
        return loaded;
    }

    // 限制, 只能使用java的类, 不能使用rojlib的类
    // 因为‘我编译我自己’
    // 只允许本地连接, 因为数据明文传输
    public static void premain(String agentArgs, Instrumentation inst) {
        if (inst != null) instInst = inst;
        else inst = instInst;

        if (!inst.isRedefineClassesSupported()) {
            System.out.println("[HR] VM不允许类的重定义");
            return;
        }
        new HRAgent(inst, agentArgs == null || agentArgs.isEmpty() ? HRRemote.DEFAULT_PORT : Integer.parseInt(agentArgs)).start();
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    private final Instrumentation inst;
    private final int port;

    HRAgent(Instrumentation inst, int port) {
        this.inst = inst;
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

                byName.clear();
                for (Class<?> clazz : inst.getAllLoadedClasses())
                    byName.put(clazz.getName(), clazz);

                while (count-- > 0) {
                    String name = in.readUTF();
                    int dataLen = in.readInt();

                    Class<?> t = byName.get(name);
                    boolean mod = t != null && inst.isModifiableClass(t);
                    if (mod) {
                        byte[] clazz = new byte[dataLen];
                        in.readFully(clazz, 0, dataLen);
                        toTransform[found++] = new ClassDefinition(t, clazz);
                    } else {
                        if (in.skipBytes(dataLen) < dataLen) throw new EOFException();
                        if (t != null) {
                            System.out.println("[HR] 此类无法更新: " + name);
                        }
                    }
                }

                if (found > 0) {
                    if (toTransform.length != found) toTransform = Arrays.copyOf(toTransform, found);
                    try {
                        inst.redefineClasses(toTransform);
                        System.out.println("[HR] 应用 " + found + " 跳过 " + (toTransform.length - found));

                        out.write(HRRemote.R_OK);
                        out.writeShort(found);
                    } catch (Throwable e) {
                        System.out.println("[HR] " + e.getMessage());

                        out.write(HRRemote.R_ERR);
                        out.writeShort(0);
                    }
                }
                socket.setSoTimeout(0);
            }
        } catch (Throwable e) {
            System.out.println("[HR] Agent断开连接 " + e.getMessage());
        }
        loaded = false;
    }
}
