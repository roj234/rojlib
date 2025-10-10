package roj.net.rpc;

import roj.net.handler.Packet;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/10/16 10:12
 */
interface ClientPacket extends Packet {void handle(RPCClientImpl client) throws IOException;}
