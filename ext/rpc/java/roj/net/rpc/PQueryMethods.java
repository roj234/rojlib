package roj.net.rpc;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/16 14:24
 */
final class PQueryMethods implements ServerPacket {
	final String className;

	public PQueryMethods(String className) {this.className = className;}
	public PQueryMethods(DynByteBuf buf) {className = buf.readUTF();}

	@Override public void encode(DynByteBuf buf) {buf.putUTF(className);}
	@Override public void handle(RPCServerImpl.Session server) throws IOException {server.queryMethods(className);}
}
