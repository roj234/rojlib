package roj.net.rpc;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/16 14:24
 */
final class PInvokeMethod implements ServerPacket {
	final int transactionId;
	final int methodId;
	final DynByteBuf params;

	public PInvokeMethod(int transactionId, int methodId, DynByteBuf params) {
		this.transactionId = transactionId;
		this.params = params;
		this.methodId = methodId;
	}
	public PInvokeMethod(DynByteBuf buf) {
		this.transactionId = buf.readInt();
		this.methodId = buf.readInt();
		this.params = buf.copySlice();
		buf.rIndex = buf.wIndex();
	}

	@Override public void encode(DynByteBuf buf) {buf.putInt(transactionId).putInt(methodId).put(params);}

	@Override public void handle(RPCServerImpl.Session server) throws IOException {server.invokeMethod(transactionId, methodId, params);}
}
