package roj.io.remote.proto;

import roj.io.remote.RFSSourceClient;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/3/4 12:25
 */
public interface ClientPacket extends Packet {void handle(RFSSourceClient client) throws IOException;}
