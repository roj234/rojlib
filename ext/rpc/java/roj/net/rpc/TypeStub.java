package roj.net.rpc;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.ci.annotation.IndirectReference;
import roj.ci.annotation.Public;
import roj.config.ConfigMaster;
import roj.config.mapper.ObjectMapper;
import roj.net.handler.PacketHandler;
import roj.net.rpc.api.RemoteProcedure;
import roj.reflect.RuntimeTypeInference;
import roj.text.ParseException;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/10/16 11:00
 */
@Public
final class TypeStub {
	static final PacketHandler NETWORK = new PacketHandler(ClientPacket.class, ServerPacket.class)
			.register(PQueryMethods.class, PQueryMethods::new)
			.register(PRemoteMethods.class, PRemoteMethods::new)
			.register(PInvokeMethod.class, PInvokeMethod::new)
			.register(PInvocationResult.class, PInvocationResult::new)
			.register(PInvocationFailure.class, PInvocationFailure::new);
	static final ObjectMapper SERIALIZER = ObjectMapper.getInstance(ObjectMapper.GENERATE|ObjectMapper.CHECK_INTERFACE|ObjectMapper.CHECK_PARENT|ObjectMapper.OBJECT_POOL|ObjectMapper.NO_SCHEMA);

	@IndirectReference public static DynByteBuf getEncodeBuffer() {
		var buf = new ByteList(ArrayCache.getIOBuffer());
		buf.clear(); return buf;
	}
	@IndirectReference public static DynByteBuf encode(DynByteBuf buffer, IType type, Object instance) {encode(instance, type, buffer);return buffer;}
	@IndirectReference public static void encode(Object instance, IType type, DynByteBuf buffer) {SERIALIZER.writer(type).write(ConfigMaster.MSGPACK, Helpers.cast(instance), buffer);}
	@IndirectReference public static Object decode(DynByteBuf buffer, IType type) throws IOException, ParseException {return SERIALIZER.reader(type).read(buffer, ConfigMaster.MSGPACK);}

	Class<? extends RemoteProcedure> type;
	Object instance;
	List<MethodStub> methods;

	public <T extends RemoteProcedure> TypeStub(Class<T> type, T instance, List<MethodStub> methods) {
		this.type = type;
		this.instance = instance;
		this.methods = methods;
	}

	static <T extends RemoteProcedure> TypeStub create(Class<T> type, T instance) {
		List<MethodStub> methods = new ArrayList<>();
		ClassNode node = ClassNode.fromType(type);
		for (MethodNode method : node.methods) {
			if ((method.modifier()&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE)) != 0) continue;

			var remoteMethod = new MethodStub();
			remoteMethod.className = type.getName();
			remoteMethod.methodName = method.name();

			Signature sign = method.getSignature(node.cp);
			sign.values.replaceAll(t -> RuntimeTypeInference.substituteTypeVariables(t, typeVariable -> sign.typeVariables.get(typeVariable.name).get(0)));

			remoteMethod.argumentTypes = sign.values;
			remoteMethod.returnType = sign.values.remove(sign.values.size() - 1);

			methods.add(remoteMethod);
		}

		return new TypeStub(type, instance, methods);
	}
}
