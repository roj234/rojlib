package roj.net.rpc;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.frame.FrameVisitor;
import roj.asm.insn.CodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.ci.annotation.IndirectReference;
import roj.ci.annotation.Public;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.collect.ToIntMap;
import roj.concurrent.Promise;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.net.rpc.api.RPCClient;
import roj.net.rpc.api.RemoteProcedure;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.Pair;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/10/11 22:47
 */
@Public
public class RPCClientImpl implements RPCClient, ChannelHandler {
	private static final VirtualReference<Map<Class<?>, TypeStub>> STUB_CACHE = new VirtualReference<>();

	private final Map<Class<?>, ProxyStub> localStubImpl = new java.util.HashMap<>();
	private final List<MethodStub> localMethods = new ArrayList<>();
	private final AtomicInteger transactionId = new AtomicInteger();

	public RPCClientImpl() {}

	private ChannelCtx connection;
	private volatile Throwable error;

	private final Map<String, Pair<Object, Integer>> methodIdQueries = new roj.collect.HashMap<>();
	private final IntMap<Consumer<Object>> callbacks = new IntMap<>();
	private Promise<RPCClient> openCallback = Promise.manual();

	@Override public void handlerAdded(ChannelCtx ctx) {this.connection = ctx;}
	@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {((ClientPacket) msg).handle(this);}
	@Override public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		error = ex;
		ChannelHandler.super.exceptionCaught(ctx, ex);
	}
	@Override public void channelClosed(ChannelCtx ctx) {
		if (error == null) error = new AsynchronousCloseException();
		((Promise.Result) openCallback).reject(error);

		for (Pair<Object, Integer> value : methodIdQueries.values()) {
			Object lock = value.getKey();
			synchronized (lock) { lock.notifyAll(); }
		}
		methodIdQueries.clear();

		for (Consumer<Object> value : callbacks.values()) value.accept(error);
		callbacks.clear();
	}
	@Override public void channelOpened(ChannelCtx ctx) {
		((Promise.Result) openCallback).resolve(this);
	}

	public void attachTo(MyChannel channel) {
		channel.addLast("rpc:client:packet", TypeStub.NETWORK.client()).addLast("rpc:client", this);
	}

	public Promise<RPCClient> onOpened() {return openCallback;}

	void remoteMethods(String className, List<MethodStub> methods) {
		Pair<Object, Integer> pair;
		synchronized (methodIdQueries) {
			pair = methodIdQueries.remove(className);
		}
		if (pair == null) throw new IllegalStateException("Unexpected className "+className);

		int nearby = pair.getValue();

		var methodIds = new ToIntMap<MethodStub>(methods.size());
		for (var method : methods) methodIds.putInt(method, method.methodId);

		for (int i = nearby; i >= 0; i--) {
			MethodStub method = localMethods.get(i);
			if (!method.className.equals(className)) break;

			method.methodId = methodIds.getOrDefault(method, -1);
		}
		for (int i = nearby+1; i < localMethods.size(); i++) {
			MethodStub method = localMethods.get(i);
			if (!method.className.equals(className)) break;

			method.methodId = methodIds.getOrDefault(method, -1);
		}

		Object lock = pair.getKey();
		synchronized (lock) { lock.notifyAll(); }
	}

	void invocationResult(int transactionId, Object returnValue) {
		Consumer<Object> callback;
		synchronized (callbacks) {
			callback = callbacks.remove(transactionId);
		}

		if (callback == null) throw new IllegalStateException("Unexpected transaction #"+transactionId);
		callback.accept(returnValue);
	}

	@IndirectReference
	@Public
	static DynByteBuf invokeRemoteMethod(DynByteBuf params, RPCClientImpl self, int localMethodId) throws IOException {
		checkAsyncClose(self);

		var remoteMethod = self.localMethods.get(localMethodId);

		if (remoteMethod.methodId == 0) {
			Pair<Object, Integer> pair, existing;
			synchronized (self.methodIdQueries) {
				pair = new Pair<>(new PQueryMethods(remoteMethod.className), localMethodId);
				existing = self.methodIdQueries.putIfAbsent(remoteMethod.className, pair);
			}
			if (existing != null) pair = existing;
			else self.connection.channel().fireChannelWrite(pair.getKey());

			while (remoteMethod.methodId == 0) {
				Object lock = pair.getKey();
				synchronized (lock) {
					try {
						lock.wait();
					} catch (InterruptedException e) {
						throw IOUtil.rethrowAsIOException(e);
					}
				}

				checkAsyncClose(self);
			}
		}

		int remoteMethodId = remoteMethod.methodId;
		if (remoteMethodId < 0) throw new RemoteException("(from remote) No implementation for "+remoteMethod.className+"."+remoteMethod.methodName);

		int transactionId = self.transactionId.getAndIncrement();
		var packet = new PInvokeMethod(transactionId, remoteMethodId, self.connection.allocate(true, params.readableBytes()).put(params));

		params.release();

		var promise = Promise.manual();

		self.callbacks.put(transactionId, promise::resolve);
		self.connection.channel().fireChannelWrite(packet);

		try {
			Object o = promise.get();
			if (o instanceof Throwable e)
				Helpers.athrow(e);
			return (DynByteBuf) o;
		} catch (InterruptedException e) {
			throw IOUtil.rethrowAsIOException(e);
		} catch (ExecutionException e) {
			Helpers.athrow(e.getCause());
			return null;
		}
	}

	private static void checkAsyncClose(RPCClientImpl self) throws IOException {
		if (self.error != null) throw new IOException("Stream closed", self.error);
	}

	@Override
	public <T extends RemoteProcedure> T getImplementation(Class<T> type) {
		ProxyStub stub = localStubImpl.get(type);
		if (stub != null) return type.cast(stub);

		Map<Class<?>, TypeStub> cache = STUB_CACHE.computeIfAbsent(type.getClassLoader(), Helpers.fnHashMap());

		var typeStub = cache.get(type);
		if (typeStub == null) {
			typeStub = TypeStub.create(type, null);
			typeStub.instance = createProxyStub(typeStub);

			synchronized (cache) {
				var stub1 = cache.putIfAbsent(type, typeStub);
				if (stub1 != null) typeStub = stub1;
			}
		}

		synchronized (localStubImpl) {
			stub = ((ProxyStub) typeStub.instance).copyWith(this, localMethods.size());
			var oldStub = localStubImpl.putIfAbsent(type, stub);
			if (oldStub == null) {
				for (MethodStub method : typeStub.methods) {
					localMethods.add(method.clone());
				}
			} else {
				stub = oldStub;
			}
		}
		return type.cast(stub);
	}

	@Override
	public void close() throws IOException {
		var conn = connection;
		if (conn != null) conn.channel().closeGracefully();
	}

	@Public
	private interface ProxyStub {
		ProxyStub copyWith(RPCClientImpl handler, int localMethodOffset);
	}

	@NotNull
	private static ProxyStub createProxyStub(TypeStub stub) {
		var myClass = new ClassNode();
		myClass.version = ClassNode.JavaVersion(8);
		myClass.name("roj/net/rpc/AsmStub$"+Reflection.uniqueId());
		myClass.addInterface("roj/net/rpc/RPCClientImpl$ProxyStub");
		myClass.addInterface(stub.type.getName().replace('.', '/'));

		myClass.defaultConstructor();

		int clientFieldId = myClass.newField(ACC_PRIVATE, "client", "Lroj/net/rpc/RPCClientImpl;");
		int methodIdOffsetId = myClass.newField(ACC_PRIVATE, "client", "I");

		CodeWriter cw1 = myClass.newMethod(ACC_PUBLIC | ACC_FINAL, "copyWith", "(Lroj/net/rpc/RPCClientImpl;I)Lroj/net/rpc/RPCClientImpl$ProxyStub;");
		cw1.visitSize(3, 3);
		cw1.newObject(myClass.name());
		cw1.insn(DUP);
		cw1.insn(ALOAD_1);
		cw1.field(PUTFIELD, myClass, clientFieldId);
		cw1.insn(DUP);
		cw1.insn(ILOAD_2);
		cw1.field(PUTFIELD, myClass, methodIdOffsetId);
		cw1.insn(ARETURN);

		var helper = new LoadType(myClass);

		for (MethodStub method : stub.methods) {
			CodeWriter cw = myClass.newMethod(ACC_PUBLIC | ACC_FINAL, method.methodName, Type.getMethodDescriptor(method.argumentTypes, method.returnType));
			cw.computeFrames(FrameVisitor.COMPUTE_SIZES);

			cw.invokeS("roj/net/rpc/TypeStub", "getEncodeBuffer", "()Lroj/util/DynByteBuf;");

			int slot = 1;
			List<IType> argumentTypes = method.argumentTypes;
			for (int i = 0; i < argumentTypes.size(); i++) {
				IType argumentType = argumentTypes.get(i);
				Type rawType = argumentType.rawType();

				if (argumentType.isPrimitive()) {
					cw.varLoad(rawType, slot);
					cw.invokeV("roj/util/DynByteBuf", "put"+rawType.capitalized(), "("+(char) rawType.type+")Lroj/util/DynByteBuf;");
				} else {
					helper.loadType(cw, argumentType);
					cw.varLoad(rawType, slot);
					cw.invokeS("roj/net/rpc/TypeStub", "encode", "(Lroj/util/DynByteBuf;Lroj/asm/type/IType;Ljava/lang/Object;)Lroj/util/DynByteBuf;");
				}

				slot += rawType.length();
			}

			cw.insn(ALOAD_0);
			cw.field(GETFIELD, myClass, clientFieldId);
			cw.insn(ALOAD_0);
			cw.field(GETFIELD, myClass, methodIdOffsetId);
			cw.ldc(method.methodId);
			cw.insn(IADD);
			cw.invokeS("roj/net/rpc/RPCClientImpl", "invokeRemoteMethod", "(Lroj/util/DynByteBuf;Lroj/net/rpc/RPCClientImpl;I)Lroj/util/DynByteBuf;");

			IType returnType = method.returnType;
			if (returnType == Type.VOID_TYPE) {
				cw.insn(RETURN);
			} else if (returnType.isPrimitive()) {
				var rawType = returnType.rawType();
				cw.invokeV("roj/util/DynByteBuf", "read"+rawType.capitalized(), "()"+(char) rawType.type);
			} else {
				helper.loadType(cw, returnType);
				cw.invokeS("roj/net/rpc/RPCServerImpl$Invoker", "decode", "(Lroj/util/DynByteBuf;Lroj/asm/type/IType;)Ljava/lang/Object;");
				cw.clazz(CHECKCAST, returnType.rawType().getActualClass());
				cw.insn(ARETURN);
			}
			cw.return_(returnType.rawType());
		}

		helper.clinit.insn(RETURN);

		return (ProxyStub) Reflection.createInstance(stub.type, myClass);
	}
}
