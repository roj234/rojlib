package roj.io.remote.proto;

import roj.collect.ArrayList;
import roj.io.ByteInput;
import roj.io.remote.RFSSourceClient;
import roj.io.remote.RemoteFileSystem;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/3/4 11:35
 */
public interface Packet {
    PacketSerializer SERIALIZER = new PacketSerializer()
            .register(Fail.class, Fail::new)
            .register(GetFileMetadata.class, GetFileMetadata::new)
            .register(FileMetadata.class, FileMetadata::new)
            .register(DirectoryMeta.class, DirectoryMeta::new)
            .register(OpenHandle.class, OpenHandle::new)
            .register(FileHandle.class, FileHandle::new)
            .register(FileHandleOp.class, FileHandleOp::new)
            .register(FileSegmentHashRequest.class, FileSegmentHashRequest::new)
            .register(FileData.class, FileData::new)
            .register(AccessToken.class, AccessToken::new);

    void encode(DynByteBuf buf);

    final class Fail implements ServerPacket, ClientPacket {
        public final String message;

        public Fail(String message) {this.message = message;}
        public Fail(DynByteBuf buf) {message = buf.readVUIGB();}
        public void encode(DynByteBuf buf) {buf.putVUIGB(message);}

        public void handle(RFSSourceClient client) throws IOException {client.onFail(message);}
        public void handle(RemoteFileSystem.Session server) {server.onFail(message);}
    }

    final class GetFileMetadata implements ServerPacket {
        public final String path;

        public GetFileMetadata(String path) {this.path = path;}
        public GetFileMetadata(DynByteBuf buf) {path = buf.readVUIGB(1024);}
        public void encode(DynByteBuf buf) {buf.putVUIGB(path);}

        public void handle(RemoteFileSystem.Session server) throws IOException {server.onGetFileMeta(path);}
    }
    class FileMetadata implements ClientPacket {
        public String path;
        public long winModTime, winAccTime, winCreTime;
        public long length;
        public int flags;

        public FileMetadata(Path path) throws IOException {
            var attr = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
            length = attr.size();
            winAccTime = attr.lastAccessTime().to(TimeUnit.MICROSECONDS);
            winCreTime = attr.creationTime().to(TimeUnit.MICROSECONDS);
            winModTime = attr.lastModifiedTime().to(TimeUnit.MICROSECONDS);
        }
        public FileMetadata(DynByteBuf buf) {path = buf.readVUIGB(1024);winModTime = buf.readLong(); winAccTime = buf.readLong(); winCreTime = buf.readLong(); length = buf.readVULong(); flags = buf.readInt();}


        public void encode(DynByteBuf buf) {buf.putVUIGB(path).putLong(winModTime).putLong(winAccTime).putLong(winCreTime).putVULong(length).putInt(flags);}

        public void handle(RFSSourceClient client) {client.handleFileMeta(this);}
    }
    final class DirectoryMeta extends FileMetadata {
        public List<FileMetadata> files;

        public DirectoryMeta(Path path) throws IOException {
            super(path);
            files = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
                for (Path path2 : ds) {
                    files.add(new FileMetadata(path2));
                }
            }
        }
        public DirectoryMeta(DynByteBuf buf) {
            super(buf);
            int length = buf.readVUInt();
            FileMetadata[] files = new FileMetadata[length];
            for (int i = 0; i < length; i++) {
                files[i] = new FileMetadata(buf);
            }
            this.files = Arrays.asList(files);
        }
        public void encode(DynByteBuf buf) {
            super.encode(buf);
            buf.putVUInt(files.size());
            for (int i = 0; i < files.size(); i++) {
                files.get(i).encode(buf);
            }
        }

        public void handle(RFSSourceClient client) {client.handleDirectoryContent(this);}
    }

    final class OpenHandle implements ServerPacket {
        public final String path;
        public final int openMode;
        public static final int OPEN_READ = 1;
        public static final int OPEN_WRITE = 2;

        public OpenHandle(String path, int mode) {this.path = path;this.openMode = mode;}
        public OpenHandle(DynByteBuf buf) {path = buf.readVUIGB(1024);openMode = buf.readByte();}
        public void encode(DynByteBuf buf) {buf.putVUIGB(path).put(openMode);}

        public void handle(RemoteFileSystem.Session server) throws IOException {server.onOpenHandle(this);}
    }
    final class FileHandle implements ClientPacket, ServerPacket {
        public static final int ENOSHCHFILE = -1;
        public static final int EINSUFFICIENTPERMISSION = -2;
        public static final int EINTERNALERROR = -3;
        public static final int EOPENLIMIT = -4;
        public final int handle;
        public final long length;

        public String error() {
            return switch (handle) {
                default -> String.valueOf(handle);
                case ENOSHCHFILE -> "ENOSUCHFILE";
                case EINSUFFICIENTPERMISSION -> "EINSUFFICIENTPERMISSION";
                case EINTERNALERROR -> "EINTERNALERROR";
                case EOPENLIMIT -> "EOPENLIMIT";
            };
        }

        public FileHandle(int handle, long length) {this.handle = handle;this.length = length;}
        public FileHandle(DynByteBuf buf) {handle = ByteInput.zag(buf.readVUInt());length = buf.readVULong();}
        public void encode(DynByteBuf buf) {buf.putVUInt(DynByteBuf.zig(handle)).putVULong(length);}

        public void handle(RFSSourceClient client) {client.onOpenHandle(this);}
        public void handle(RemoteFileSystem.Session server) throws IOException {server.onCloseHandle(this);}
    }
    final class FileHandleOp implements ServerPacket {
        public final int handle;
        public final int type;
        public final long value;
        public static final int META_READ = 0, META_POSITION = 1, META_LENGTH = 2, CANCEL_FILEDATA = 3;

        public FileHandleOp(int handle, int type, long value) {this.handle = handle;this.type = type;this.value = value;}
        public FileHandleOp(DynByteBuf buf) {handle = buf.readVUInt();type = buf.readVUInt();value = buf.readLong();}
        public void encode(DynByteBuf buf) {buf.putVUInt(handle).putVUInt(type).putLong(value);}

        public void handle(RemoteFileSystem.Session server) throws IOException {server.onSourceMeta(this);}
    }
    final class FileSegmentHashRequest implements ServerPacket {
        public final int handle;
        public final long offset, length;
        public final int hashType;
        public static final int SHA256 = 1, ROLLING = 2;

        public FileSegmentHashRequest(int handle, long offset, long length, int hashType) {
            this.handle = handle;
            this.offset = offset;
            this.length = length;
            this.hashType = hashType;
        }
        public FileSegmentHashRequest(DynByteBuf buf) {
            handle = buf.readVUInt();
            offset = buf.readVULong();
            length = buf.readVULong();
            hashType = buf.readByte();
        }
        public void encode(DynByteBuf buf) {buf.putVUInt(handle).putVULong(offset).putVULong(length).put(hashType);}

        public void handle(RemoteFileSystem.Session server) throws IOException {server.onHashRequest(this);}
    }
    final class FileData implements ClientPacket, ServerPacket {
        public final int handle;
        public DynByteBuf data;
        private Consumer<DynByteBuf> writer;

        public FileData(int handle, Consumer<DynByteBuf> writer) {this.handle = handle;this.writer = writer;}
        public FileData(DynByteBuf buf) {handle = buf.readVUInt();data = buf.slice(buf.readableBytes());writer = x -> x.put(data);}
        public void encode(DynByteBuf buf) {writer.accept(buf.putVUInt(handle));}

        public void handle(RFSSourceClient client) throws IOException {client.onFileData(this);}
        public void handle(RemoteFileSystem.Session server) throws IOException {server.onFileData(this);}
    }

    final class AccessToken implements ClientPacket, ServerPacket {
        public final String accessToken;

        public AccessToken(String accessToken) {this.accessToken = accessToken;}
        public AccessToken(DynByteBuf buf) {this.accessToken = buf.readVUIGB(200);}
        public void encode(DynByteBuf buf) {buf.putVUIGB(accessToken);}

        public void handle(RFSSourceClient client) throws IOException {client.onLogin(accessToken);}
        public void handle(RemoteFileSystem.Session server) throws IOException {server.onAccessToken(accessToken);}
    }
}
