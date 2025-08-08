package roj.io.remote;

import roj.collect.BitSet;
import roj.collect.IntMap;
import roj.config.data.CMap;
import roj.crypt.KeyType;
import roj.util.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.net.*;
import roj.net.mss.MSSHandler;
import roj.net.handler.Timeout;
import roj.net.mss.MSSContext;
import roj.net.mss.MSSKeyPair;
import roj.plugin.PermissionHolder;
import roj.plugin.Plugin;
import roj.plugin.PluginDescriptor;
import roj.plugin.PluginManager;
import roj.io.remote.proto.Packet;
import roj.io.remote.proto.ServerPacket;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/3/4 3:54
 */
public class RemoteFileSystem extends Plugin implements Consumer<MyChannel> {
    private static final ChannelHandler PACKET_SERIALIZER = Packet.SERIALIZER.server();
    static final Logger LOGGER = Logger.getLogger();

    private final MSSContext context = new MSSContext().setALPN("RFS");
    private ServerLaunch launch;

    private Plugin easySso;
    private PermissionHolder anonymousPermissions = PermissionHolder.NONE;

    @Override
    protected void onEnable() throws Exception {
        PluginManager pm = getPluginManager();
        CMap config = getConfig();
        keypairGot: {
            var keyStore = pm.getPluginInstance("keyStore");
            if (keyStore != null) {
                MSSKeyPair keyPair = keyStore.ipc(new TypedKey<MSSKeyPair>("getDefaultMSSKeypair"));
                if (keyPair != null) {
                    context.setCertificate(keyPair);
                    break keypairGot;
                }

                context.setCertificate(new MSSKeyPair(KeyType.getInstance("EdDSA").loadOrGenerateKey(new File(getDataFolder(), "roj234.EdDSA.key"), config.getString("key_pass").getBytes(StandardCharsets.UTF_8))));
            }
        }
        easySso = getPluginManager().getPluginInstance(PluginDescriptor.Role.PermissionManager);
		anonymousPermissions = easySso.ipc(new TypedKey<>("getDefaultPermissions"));

		if (!config.getString("address").isEmpty()) {
            var bindAddress = Net.parseAddress(config.getString("address"), InetAddress.getLocalHost());
            launch = ServerLaunch.tcp(config.getString("server_name"))
                    .bind(bindAddress)
                    .option(StandardSocketOptions.SO_REUSEADDR, true)
                    .initializator(this).launch();
        } else {
            launch = ServerLaunch.shadow(config.getString("server_name"), this);
        }

        getLogger().info("RFS启动成功！");
    }

    @Override
    protected void onDisable() {
        IOUtil.closeSilently(launch);
    }

    @Override
    public void accept(MyChannel ch) {
        ch.addLast("mss", new MSSHandler(context.serverEngine()))
          .addLast("timeout", new Timeout(60000, 5000))
          .addLast("packet", PACKET_SERIALIZER)
          .addLast("server", new Session());
    }

    public class Session implements ChannelHandler {
        public String user = "admin";
        ChannelCtx ctx;

        IntMap<Source> handles = new IntMap<>();
        int nextHandle;
        BitSet closedHandles = new BitSet();

        PermissionHolder permissions = anonymousPermissions;
        int pendingHandle;
        long pendingCount;

        public String transformPath(String path) {return path;}

        public Session() {}

        @Override
        public void channelOpened(ChannelCtx ctx) throws IOException {
            LOGGER.info(ctx+" 连接");
            this.ctx = ctx;
            ctx.channelWrite(new Packet.AccessToken(easySso.getDescription().getId()));
        }

        @Override
        public void channelClosed(ChannelCtx ctx) throws IOException {
            for (Source value : handles.values()) {
                IOUtil.closeSilently(value);
            }
            LOGGER.info(ctx+" 断开");
        }

        @Override
        public void channelRead(ChannelCtx ctx, Object msg) throws IOException {((ServerPacket) msg).handle(this);}

        @Override
        public void channelTick(ChannelCtx ctx) throws Exception {
            tickPending();
        }

        public void onFail(String message) {
            if (message.isEmpty()) {
                // ping
                return;
            }
            LOGGER.info("Error " + user + ": " + message);
        }

        public void onGetFileMeta(String path) throws IOException {
            path = transformPath(path);

            if (!permissions.isReadable(path)) {
                ctx.channelWrite(new Packet.Fail("Permission denied"));
            } else {
                Path path1 = Paths.get(path);
                Packet.FileMetadata r = Files.isDirectory(path1) ? new Packet.DirectoryMeta(path1) : new Packet.FileMetadata(path1);
                ctx.channelWrite(r);
            }
        }

        public void onOpenHandle(Packet.OpenHandle packet) throws IOException {
            String path = transformPath(packet.path);

            if (packet.openMode == Packet.OpenHandle.OPEN_WRITE) {
                if (!permissions.isWritable(path)) {
                    ctx.channelWrite(new Packet.FileHandle(Packet.FileHandle.EINSUFFICIENTPERMISSION, 0));
                    return;
                }
            } else {
                if (!permissions.isReadable(path)) {
                    ctx.channelWrite(new Packet.FileHandle(Packet.FileHandle.EINSUFFICIENTPERMISSION, 0));
                    return;
                }
            }
            if (handles.size() > 100) {
                ctx.channelWrite(new Packet.FileHandle(Packet.FileHandle.EOPENLIMIT, 0));
                return;
            }
            if (!new File(path).isFile()) {
                ctx.channelWrite(new Packet.FileHandle(Packet.FileHandle.ENOSHCHFILE, 0));
                return;
            }

            int handle;
            if ((handle = closedHandles.first()) < 0) {
                handle = nextHandle++;
            } else {
                closedHandles.remove(handle);
            }

            var source = new FileSource(new File(path), packet.openMode == Packet.OpenHandle.OPEN_WRITE);
            handles.put(handle, source);

            ctx.channelWrite(new Packet.FileHandle(handle, source.length()));
        }

        public void onSourceMeta(Packet.FileHandleOp packet) throws IOException {
            var source = handles.get(packet.handle);
            if (source == null) {
                ctx.channelWrite(new Packet.Fail("Handle closed"));
                return;
            }

            switch (packet.type) {
                case Packet.FileHandleOp.META_READ -> {
                    pendingHandle = packet.handle;
                    pendingCount = packet.value;
                    tickPending();
                }
                case Packet.FileHandleOp.META_POSITION -> {
                    source.seek(packet.value);
                    ctx.channelWrite(new Packet.Fail(""));
                }
                case Packet.FileHandleOp.META_LENGTH -> {
                    source.setLength(packet.value);
                    ctx.channelWrite(new Packet.Fail(""));
                }
                case Packet.FileHandleOp.CANCEL_FILEDATA -> {
                    pendingCount = 0;
                }
                default -> {
                    ctx.channelWrite(new Packet.Fail("Invalid argument"));
                }
            }
        }

        private void tickPending() throws IOException {
            if (pendingCount == 0) return;

            var source = handles.get(pendingHandle);
            if (source == null) {
                pendingCount = 0;
                return;
            }

            var once = Math.min(0x10000, pendingCount);
            pendingCount -= once;

            ctx.channelWrite(new Packet.FileData(pendingHandle, buf -> {
                if (buf instanceof ByteList bl) {
                    try {
                        source.read(bl, (int) once);
                    } catch (IOException e) {
                        Helpers.athrow(e);
                    }
                }
                else
                    throw new FastFailException("No supported at this time");
            }));
        }

        public void onFileData(Packet.FileData packet) throws IOException {
            var source = handles.get(packet.handle);
            if (source == null) {
                ctx.channelWrite(new Packet.Fail("Handle closed"));
                return;
            }
            source.write(packet.data);
        }

        public void onHashRequest(Packet.FileSegmentHashRequest packet) throws IOException {
            ctx.channelWrite(new Packet.Fail("No supported at this time"));
        }

        public void onCloseHandle(Packet.FileHandle packet) {
            Source closed = handles.remove(packet.handle);
            IOUtil.closeSilently(closed);
            if (closed != null) closedHandles.add(packet.handle);
        }

        public void onAccessToken(String accessToken) throws IOException {
            var user = easySso.ipc(new TypedKey<PermissionHolder>("authenticateUser"), accessToken);

            if (user == null || permissions != anonymousPermissions) {
                ctx.channelWrite(new Packet.Fail("Invalid token"));
                ctx.channel().closeGracefully();
                return;
            }

            getLogger().info(this+" Successfully login via AK");
            permissions = user;
            ctx.channelWrite(new Packet.Fail(""));
        }
    }
}
