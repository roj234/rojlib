package roj.plugins.rfs.proto;

import roj.plugins.rfs.RemoteFileSystem;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/3/4 12:25
 */
public interface ServerPacket extends Packet {void handle(RemoteFileSystem.Session server) throws IOException;}
