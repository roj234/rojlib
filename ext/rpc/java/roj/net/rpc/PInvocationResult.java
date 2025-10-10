package roj.net.rpc;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/16 14:24
 */
final class PInvocationResult implements ClientPacket {
	final int transactionId;
	final DynByteBuf returnValue;

	public PInvocationResult(int transactionId, DynByteBuf returnValue) {
		this.transactionId = transactionId;
		this.returnValue = returnValue;
	}
	public PInvocationResult(DynByteBuf buf) {
		this.transactionId = buf.readInt();
		this.returnValue = buf.copySlice();
		buf.rIndex = buf.wIndex();
	}

	@Override public void encode(DynByteBuf buf) {buf.putInt(transactionId).put(returnValue);}

	@Override public void handle(RPCClientImpl client) throws IOException {
		client.invocationResult(transactionId, returnValue);
	}
}
