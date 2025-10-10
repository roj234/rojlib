package roj.plugins.remotefs.proto;

import roj.net.handler.Packet;
import roj.plugins.remotefs.RemoteFSClient;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/3/4 12:25
 */
public interface ClientPacket extends Packet {void handle(RemoteFSClient client) throws IOException;}
