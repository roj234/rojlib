package roj.net.rpc;

import roj.net.handler.Packet;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/16 10:12
 */
interface ServerPacket extends Packet {void handle(RPCServerImpl.Session server) throws IOException;}
