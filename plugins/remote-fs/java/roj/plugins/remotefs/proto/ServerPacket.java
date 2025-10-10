package roj.plugins.remotefs.proto;

import roj.net.handler.Packet;
import roj.plugins.remotefs.RemoteFileSystem;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/3/4 12:25
 */
public interface ServerPacket extends Packet {void handle(RemoteFileSystem.Session server) throws IOException;}
