package roj.net.rpc;

import roj.config.ConfigMaster;
import roj.config.MsgPackEncoder;
import roj.config.mapper.ObjectReader;
import roj.text.ParseException;
import roj.util.DynByteBuf;
import roj.util.FastFailException;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/16 14:24
 */
final class PInvocationFailure implements ClientPacket {
	final int transactionId;
	Throwable exception;

	public PInvocationFailure(int transactionId, Throwable exception) {
		this.transactionId = transactionId;
		this.exception = exception;
	}
	public PInvocationFailure(DynByteBuf buf) {
		this.transactionId = buf.readInt();
		this.exception = new FastFailException(buf.readVUIUTF());

		ObjectReader<StackTraceElement[]> reader = TypeStub.SERIALIZER.reader(StackTraceElement[].class);
		try {
			exception.setStackTrace(reader.read(buf, ConfigMaster.MSGPACK));
		} catch (IOException | ParseException e) {
			exception.addSuppressed(e);
		}
	}

	@Override public void encode(DynByteBuf buf) {
		buf.putInt(transactionId).putVUIUTF(exception.toString());
		StackTraceElement[] stackTrace = exception.getStackTrace();

		int i = stackTrace.length - 1;
		for (; i >= 0; i--) {
			StackTraceElement stackTraceElement = stackTrace[i];
			if (stackTraceElement.getClassName().equals("roj.net.rpc.RPCServerImpl$Session")) {
				break;
			}
		}

		var newStackTrace = new StackTraceElement[i+1];
		System.arraycopy(stackTrace, 0, newStackTrace, 0, i+1);

		var writer = TypeStub.SERIALIZER.writer(StackTraceElement[].class);
		writer.write(new MsgPackEncoder(buf), newStackTrace);
	}

	@Override public void handle(RPCClientImpl client) throws IOException {client.invocationResult(transactionId, new RemoteException("(from remote)", exception));}
}
