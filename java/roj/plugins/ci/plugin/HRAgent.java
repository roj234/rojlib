package roj.plugins.ci.plugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj233
 * @since 2022/2/21 15:08
 */
public class HRAgent extends Thread implements ClassFileTransformer {
	private static Instrumentation inst;
	public static Instrumentation getInstrumentation() {return inst;}

	public static void agentmain(String agentArgs, Instrumentation inst) throws Exception { premain(agentArgs, inst); }
	public static void premain(String agentArgs, Instrumentation inst) throws Exception {
		if (inst != null) HRAgent.inst = inst;
		else inst = HRAgent.inst;

		if (!inst.isRedefineClassesSupported()) {
			System.out.println("[热重载青春版] VM不允许类的重定义");
			return;
		}

		int port = (agentArgs == null || agentArgs.isEmpty()) ? 4485 : Integer.parseInt(agentArgs);
		System.out.println("[热重载青春版] Agent正在连接"+new InetSocketAddress(InetAddress.getLocalHost(), port));
		HRAgent agent = new HRAgent(port);
		inst.addTransformer(agent);
		agent.start();
	}

	private final int port;
	private final ConcurrentHashMap<String, byte[]> loadPending = new ConcurrentHashMap<>();

	HRAgent(int port) {
		this.port = port;
		setName("RojLib HotReload Agent");
		setDaemon(true);
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> redef, ProtectionDomain pd, byte[] buf) throws IllegalClassFormatException {
		return loadPending.remove(className);
	}

	@Override
	public void run() {
		int time = 500;
		while (true) {
			try (var socket = new Socket()) {
				socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), time);
				System.out.println("[热重载青春版] 服务器连接成功");
				time = 500;

				DataInputStream din = new DataInputStream(socket.getInputStream());
				DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
				dout.writeUTF("FMD/HR/1");

				while (true) {
					socket.setSoTimeout(0);
					byte control = (byte) din.read();
					if (control < 0) break;

					socket.setSoTimeout(1000);

					int classCount = din.readUnsignedShort();
					var changed = new ClassDefinition[classCount];

					HashMap<String, Class<?>> classes = new HashMap<>();
					for (var type : inst.getAllLoadedClasses()) {
						classes.put(type.getName(), type);
					}

					int i = 0;
					while (classCount-- > 0) {
						String name = din.readUTF();
						Class<?> type = classes.get(name);

						int length = din.readInt();
						byte[] data = new byte[length];
						din.readFully(data, 0, length);

						if (type == null) {
							loadPending.put(name, data);
							System.out.println("[热重载青春版] 需要更新的类尚未加载: " + name);
						} else if (inst.isModifiableClass(type)) {
							changed[i++] = new ClassDefinition(type, data);
						} else {
							din.skipBytes(length);
							System.out.println("[热重载青春版] 此类无法更新: " + name);
						}
					}

					if (i > 0) {
						int initLen = changed.length;
						if (initLen != i) changed = Arrays.copyOf(changed, i);

						int success;
						try {
							inst.redefineClasses(changed);
							success = i;
						} catch (Throwable e) {
							var tmp = new ClassDefinition[1];
							success = 0;

							for (int j = 0; j < changed.length; j++) {
								tmp[0] = changed[j];
								try {
									inst.redefineClasses(tmp);
									success++;
								} catch (Throwable ex) {
									System.out.println("[热重载青春版] 失败: "+ex);
									dout.writeUTF(ex.toString());
								}
							}
						}

						String msg = "[热重载青春版] 重载结果 成功 "+success+" 失败 "+(i-success)+" 跳过 "+(initLen - i);
						System.out.println(msg);
						dout.writeUTF(msg);
						dout.writeShort(0);
					}
					socket.setSoTimeout(0);
				}

				System.out.println("[热重载青春版] 连接关闭");
				break;
			} catch (ConnectException | SocketTimeoutException e) {
				if (time < 10000) System.out.println("[热重载青春版] 连接超时");
				try {
					Thread.sleep(time);
				} catch (InterruptedException ignored) {}
				if (time < 10000) time <<= 1;
			} catch (Throwable e) {
				System.out.println("[热重载青春版] 连接中止 "+e);
				if (!e.getMessage().equals("Connection reset")) break;
			}
		}
	}
}