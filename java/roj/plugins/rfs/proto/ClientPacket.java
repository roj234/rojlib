package roj.plugins.rfs.proto;

import roj.plugins.rfs.RFSSourceClient;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/3/4 0004 12:25
 */
public interface ClientPacket extends Packet {void handle(RFSSourceClient client) throws IOException;}
