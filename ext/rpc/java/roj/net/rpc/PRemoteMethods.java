package roj.net.rpc;

import roj.asm.type.Signature;
import roj.collect.ArrayList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/10/16 14:24
 */
final class PRemoteMethods implements ClientPacket {
	String className;
	List<MethodStub> methods;

	public PRemoteMethods(String className, List<MethodStub> methods) {
		this.className = className;
		this.methods = methods;
	}
	public PRemoteMethods(DynByteBuf buf) {
		className = buf.readVUIUTF();
		int len = buf.readVUInt();
		methods = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			MethodStub stub = new MethodStub();
			methods.add(stub);
			stub.className = className;
			stub.methodName = buf.readVUIUTF();
			int argc = buf.readVUInt();
			stub.argumentTypes = new ArrayList<>(argc);
			for (int j = 0; j < argc; j++) {
				stub.argumentTypes.add(Signature.parseGeneric(buf.readVUIUTF()));
			}
			stub.returnType = Signature.parseGeneric(buf.readVUIUTF());
			stub.methodId = buf.readVUInt();
		}
	}

	@Override public void encode(DynByteBuf buf) {
		buf.putVUIUTF(className).putVUInt(methods.size());
		for (MethodStub method : methods) {
			buf.putVUIUTF(method.methodName);
			buf.putVUInt(method.argumentTypes.size());
			for (int i = 0; i < method.argumentTypes.size(); i++) {
				buf.putVUIUTF(method.argumentTypes.get(i).toDesc());
			}
			buf.putVUIUTF(method.returnType.toDesc());
			buf.putVUInt(method.methodId);
		}
	}

	@Override public void handle(RPCClientImpl client) throws IOException {client.remoteMethods(className, methods);}
}
