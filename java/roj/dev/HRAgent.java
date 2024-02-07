package roj.dev;

import roj.asm.Parser;
import roj.dev.hr.HRContext;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.ClientLaunch;
import roj.net.ch.handler.Compress;
import roj.net.ch.handler.VarintSplitter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;

/**
 * @author Roj233
 * @since 2022/2/21 15:08
 */
public class HRAgent implements ChannelHandler {
	private static boolean loaded;
	private static Instrumentation instInst;
	private static HRContext context;

	public static void setContext(HRContext context) {
		HRAgent.context = context;
	}

	public static HRContext getContext() {
		return context;
	}

	public static Instrumentation getInstInst() {
		return instInst;
	}

	public static void agentmain(String agentArgs, Instrumentation inst) throws Exception { premain(agentArgs, inst); }
	public static void premain(String agentArgs, Instrumentation inst) throws Exception {
		if (inst != null) instInst = inst;
		else inst = instInst;

		if (!inst.isRedefineClassesSupported()) {
			System.err.println("[HR] VM不允许类的重定义");
			return;
		}

		int port = agentArgs == null || agentArgs.isEmpty() ? HRRemote.DEFAULT_PORT : Integer.parseInt(agentArgs);
		ClientLaunch.tcp("RojLib HotReload Agent V2").initializator(ch -> {
			ch.addLast("Length", VarintSplitter.twoMbVLUI())
			  .addLast("Compress", new Compress())
			  .addLast("Handler", new HRAgent());
		}).connect(InetAddress.getLoopbackAddress(), port).launch();
	}

	@Override
	public void channelOpened(ChannelCtx ctx) { System.out.println("[HR] Agent已启动"); }
	@Override
	public void channelClosed(ChannelCtx ctx) { System.out.println("[HR] Agent断开连接"); }

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = ((DynByteBuf) msg);
		int op = buf.readUnsignedByte();
		switch (op) {
			case 0: // class data
				context.update(Parser.parseConstants(buf));
			break;
			case 1: // commit
				ByteList out = IOUtil.getSharedByteBuf();
				int found = context.dirty.size();
				try {
					context.commit(instInst);
					out.writeUTF("成功："+found);
				} catch (Throwable e) {
					out.writeUTF(e.toString());
				}
				ctx.channelWrite(out);
				break;
			case 2: // close
				ctx.close();
			break;
		}
	}
}
