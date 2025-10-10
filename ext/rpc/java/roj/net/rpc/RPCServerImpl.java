package roj.net.rpc;

import roj.asm.ClassNode;
import roj.asm.frame.FrameVisitor;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.ci.annotation.Public;
import roj.concurrent.Executor;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.net.rpc.api.RPCClient;
import roj.net.rpc.api.RPCServer;
import roj.net.rpc.api.RemoteProcedure;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.util.*;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/10/13 1:24
 */
public class RPCServerImpl implements RPCServer, RPCClient {
	static final Logger LOGGER = Logger.getLogger("RPC/Server");
	static final VirtualReference<Map<Class<?>, TypeStub>> INVOKERS = new VirtualReference<>();

	final Map<String, ProxyStub> impls = new HashMap<>();
	final List<MethodStub> methodStubs = new ArrayList<>();
	final Executor executor;

	public RPCServerImpl(Executor executor) {
		this.executor = executor;
		this.methodStubs.add(null);
	}

	public void attachTo(MyChannel channel) {
		channel.addLast("rpc:server:packet", TypeStub.NETWORK.server()).addLast("rpc:server", new Session());
	}

	@Override
	public <T extends RemoteProcedure> T getImplementation(Class<T> type) throws RemoteException {
		var procedureInfo = impls.get(type.getName());
		if (procedureInfo == null) throw new RemoteException("找不到类型 "+type+" 的实现");
		return type.cast(procedureInfo.instance);
	}

	@Override
	public synchronized <T extends RemoteProcedure> void registerImplementation(Class<T> type, T instance) {
		if (impls.containsKey(type.getName())) throw new IllegalStateException("类型 "+type+" 的实现已经注册");

		var invokers = INVOKERS.computeIfAbsent(type.getClassLoader(), Helpers.fnHashMap());
		var stub = invokers.get(type);
		if (stub == null) {
			stub = TypeStub.create(type, instance);
			stub.instance = createInvoker(type, stub);

			synchronized (invokers) {
				var stub1 = invokers.putIfAbsent(type, stub);
				if (stub1 != null) stub = stub1;
			}
		}

		int methodIdOffset = methodStubs.size();

		for (var method : stub.methods) {
			var cloneStub = method.clone();

			// relative id (over network, see #queryMethods below)
			cloneStub.methodId = methodStubs.size();
			methodStubs.add(cloneStub);
		}

		impls.put(type.getName(), new ProxyStub(stub, methodIdOffset, instance));
	}

	@Override
	public void close() {}

	private static <T extends RemoteProcedure> Invoker createInvoker(Class<T> type, TypeStub stub) {
		var myClass = new ClassNode();
		//myClass.version = ClassNode.JavaVersion(8);
		myClass.name("roj/net/rpc/AsmStub$"+Reflection.uniqueId());
		myClass.addInterface("roj/net/rpc/RPCServerImpl$Invoker");
		myClass.defaultConstructor();

		var cw = myClass.newMethod(ACC_PUBLIC | ACC_FINAL, "invoke", "(ILroj/util/DynByteBuf;Lroj/util/DynByteBuf;Ljava/lang/Object;)V");
		cw.computeFrames(FrameVisitor.COMPUTE_SIZES);
		// to compute frames
		//ClassUtil.getInstance().setResolver(new SimpleResolver(type.getClassLoader()));

		cw.insn(ILOAD_1);
		var sw = cw.tableSwitch();

		var loadType = new LoadType(myClass);
		Type instanceType = Type.getType(type);

		List<MethodStub> methods = stub.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodStub method = methods.get(i);
			method.methodId = i;
			sw.branch(method.methodId, sw.def = cw.label());

			cw.vars(ALOAD, 4);
			cw.clazz(CHECKCAST, instanceType);

			List<IType> argumentTypes = method.argumentTypes;
			for (int j = 0; j < argumentTypes.size(); j++) {
				IType argumentType = argumentTypes.get(j);
				Type rawType = argumentType.rawType();

				if (argumentType.isPrimitive()) {
					cw.insn(ALOAD_2);
					cw.invokeV("roj/util/DynByteBuf", "read" + rawType.capitalized(), "()" + (char) rawType.type);
				} else {
					cw.insn(ALOAD_2);
					loadType.loadType(cw, argumentType);
					cw.invokeS("roj/net/rpc/TypeStub", "decode", "(Lroj/util/DynByteBuf;Lroj/asm/type/IType;)Ljava/lang/Object;");
					cw.clazz(CHECKCAST, rawType.getActualClass());
				}
			}

			cw.invokeItf(instanceType.owner(), method.methodName, Type.getMethodDescriptor(method.argumentTypes, method.returnType));

			IType returnType = method.returnType;
			if (returnType.isPrimitive()) {
				if (returnType != Type.VOID_TYPE) {
					cw.insn(ALOAD_3);
					cw.insn(SWAP);

					var rawType = returnType.rawType();
					cw.invokeV("roj/util/DynByteBuf", "write" + rawType.capitalized(), "(" + (char) rawType.type + ")V");
				}
			} else {
				loadType.loadType(cw, method.returnType);
				cw.insn(ALOAD_3);
				cw.invokeS("roj/net/rpc/TypeStub", "encode", "(Ljava/lang/Object;Lroj/asm/type/IType;Lroj/util/DynByteBuf;)V");
			}

			cw.insn(RETURN);
		}

		loadType.clinit.insn(RETURN);

		return (Invoker) Reflection.createInstance(type, myClass);
	}

	private static final class ProxyStub {
		final TypeStub stub;
		final int methodIdOffset;
		final Object instance;

		private ProxyStub(TypeStub stub, int methodIdOffset, Object instance) {
			this.stub = stub;
			this.methodIdOffset = methodIdOffset;
			this.instance = instance;
		}
	}

	@Public
	private interface Invoker {
		void invoke(int methodId, DynByteBuf arguments, DynByteBuf returnType, Object instance) throws Throwable;
	}

	final class Session implements ChannelHandler {
		ChannelCtx connection;

		@Override public void handlerAdded(ChannelCtx ctx) {connection = ctx;}
		@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {((ServerPacket) msg).handle(this);}

		public void queryMethods(String className) throws IOException {
			var proxyStub = impls.get(className);
			connection.channelWrite(new PRemoteMethods(className, proxyStub == null ? Collections.emptyList() : methodStubs.subList(proxyStub.methodIdOffset, proxyStub.stub.methods.size())));
		}

		public void invokeMethod(int transactionId, int methodId, DynByteBuf arguments) {
			var method = methodStubs.get(methodId);
			var proxyStub = impls.get(method.className);
			int methodId1 = method.methodId - proxyStub.methodIdOffset;

			executor.execute(() -> {
				var invoker = (Invoker) proxyStub.stub.instance;

				ByteList returnType = new ByteList();
				Throwable exception = null;
				try {
					invoker.invoke(methodId1, arguments, returnType, proxyStub.instance);
				} catch (Throwable e) {
					LOGGER.warn("Uncaught in RPC invocation of {}", e, this);
					exception = e;
				}

				try {
					var packet = exception != null ? new PInvocationFailure(transactionId, exception) : new PInvocationResult(transactionId, returnType);
					connection.channel().fireChannelWrite(packet);
				} catch (IOException e) {
					LOGGER.warn("Exception replying {}", e, this);
				}
			});
		}
	}
}
