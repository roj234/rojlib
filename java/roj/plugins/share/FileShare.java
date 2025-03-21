package roj.plugins.share;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.collect.*;
import roj.concurrent.OperationDone;
import roj.concurrent.ScheduleTask;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.Name;
import roj.config.auto.Optional;
import roj.config.auto.SerializerFactory;
import roj.config.data.CMap;
import roj.config.serial.ToJson;
import roj.crypt.Base64;
import roj.crypt.XXHash;
import roj.http.Cookie;
import roj.http.IllegalRequestException;
import roj.http.server.*;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.plugin.PathIndexRouter;
import roj.plugin.PermissionHolder;
import roj.plugin.Plugin;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.TextUtil;
import roj.ui.Argument;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static roj.reflect.Unaligned.U;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2025/3/8 0008 0:39
 */
public class FileShare extends Plugin {
    static class ShareInfo {
        String id, name;
        long time;
        long size;
        @Optional volatile long expire;
        @Optional String text;
        @Optional String code;
        @Optional transient File base;
        @Optional List<ShareFile> files;
        transient int owner;
        transient Set<ChunkUpload.Task> uploading;

        public int expireType() {return expire == 0 ? 0 : expire > 100000 ? 1 : 2;}
        public boolean isExpired() {return expireType() == 1 && System.currentTimeMillis() > expire;}

        public void fillFromServerPath(File path) {
            files = new SimpleList<>();
            size = 0;
            base = path;
            int prefixLength = path.getAbsolutePath().length()+1;
            try {
                IOUtil.findAllFiles(path, file -> {
                    if (files.size() == 1000) throw OperationDone.INSTANCE;
                    ShareFile file1 = new ShareFile(file, prefixLength);
                    size += file1.size;
                    files.add(file1);
                    return false;
                });
            } catch (OperationDone ignored) {}
        }

        transient ShareInfo _next;

        static final long EXPIRE_OFFSET = ReflectionUtils.fieldOffset(ShareInfo.class, "expire");
        public void countDown(long timeout) {
            while (true) {
                long oldVal = this.expire;
                long newVal = oldVal == 1 ? timeout : oldVal-1;
                if (U.compareAndSwapLong(this, EXPIRE_OFFSET, oldVal, newVal)) {
                    if (oldVal == 1) code = ""; // no new entry
                    break;
                }
            }
        }
    }
    static class ShareFile {
        String name;
        @Name("type") String mime;
        @Optional String path;
        long size, lastModified;

        // Either<Integer, File> => uploadPath / file
        @Optional transient int id;
        @Optional transient File file;

        public ShareFile() {}
        public ShareFile(File file, int prefixLength) {
            this.name = file.getName();
            this.mime = MimeType.getMimeType(IOUtil.extensionName(name));
            this.size = file.length();
            this.lastModified = file.lastModified();
            String path = file.getAbsolutePath();
            if (path.length() > prefixLength+name.length())
                this.path = path.substring(prefixLength, path.length() - name.length() - 1).replace(File.separatorChar, '/');
            this.file = file;
        }
    }

    static class Serialized {
        List<ShareInfo> shares;
        int shareFileIndex;

        public Serialized() {}
        public Serialized(FileShare share) {
            shares = new SimpleList<>(share.shares);
            shareFileIndex = share.shareFileIdx.get();
        }
    }

    private final Object lock = new Object();

    private static final XHashSet.Shape<String, ShareInfo> SHARE_INFO_SHAPE = XHashSet.noCreation(ShareInfo.class, "id");
    private XHashSet<String, ShareInfo> shares = SHARE_INFO_SHAPE.create();
    private final XHashSet<String, ShareInfo> incompleteShares = SHARE_INFO_SHAPE.create();

    private File uploadPath;
    private final AtomicInteger shareFileIdx = new AtomicInteger(1);
    private final ChunkUpload uploadManager = new ChunkUpload();

    private final SerializerFactory ownerSerializer;
    private boolean dirty;

    private Plugin easySso;

    public FileShare() {
        SerializerFactory.SerializeSetting transientRemover = (owner, field, annotations) -> {
            if (field != null) {
                String name = field.name();
                if (name.equals("_next") || name.equals("file") || name.equals("uploading")) return annotations;
                field.modifier &= ~Opcodes.ACC_TRANSIENT;
            }
            return annotations;
        };
        ownerSerializer = SerializerFactory.getInstance().serializeFileToString().add(ShareFile.class, transientRemover).add(ShareInfo.class, transientRemover);
    }

    @Override
    protected void onEnable() throws Exception {
        easySso = getPluginManager().getPluginInstance("EasySSO");

        uploadPath = new File(getDataFolder(), "files");
        uploadPath.mkdir();

        CMap config = getConfig();

        reloadDB();
        getScheduler().loop(() -> {
            // 每10分钟清除1小时之前的任务
            uploadManager.purge(System.currentTimeMillis() - 3600000);
        }, 600000);

        registerCommand(literal("fileshare")
                .then(literal("remove").breakOn().then(argument("网页路径", Argument.oneOf(CollectionX.toMap(shares))).executes(ctx -> {
                    removeShare(ctx.argument("网页路径", ShareInfo.class));
                })))
                .then(literal("reload").breakOn().executes(ctx -> {
                    synchronized (lock) {
                        reloadDB();
                        if (saveLater != null) saveLater.cancel();
                        dirty = false;
                    }
                    System.out.println("已重载");
                }))
                .then(argument("网页路径", Argument.string()).then(argument("文件路径", Argument.path()).executes(ctx -> {
                    String id = ctx.argument("网页路径", String.class);
                    File path = ctx.argument("文件路径", File.class);
                    var share = new ShareInfo();
                    share.id = id;
                    share.name = "文件分享";
                    share.time = System.currentTimeMillis();
                    share.fillFromServerPath(path);
                    if (share.files.size() == 1000) getLogger().warn("文件数量超过了1000项，请通过在线预览访问全部项目");

                    synchronized (lock) {
                        if (!shares.add(share)) {
                            getLogger().warn("直链/share/{}已存在", id);
                            return;
                        }
                        markDirty();
                    }
                    getLogger().info("直链/share/{}注册成功", id);
                })))
        );

        registerRoute(config.getString("path"), new OKRouter().addPrefixDelegation("", new ZipRouter(getDescription().getArchive(), "web/")).register(this), "PermissionManager");
    }

    private void reloadDB() throws IOException, ParseException {
        var persist = new File(getDataFolder(), "db.yml");
        if (persist.isFile()) {
            var db = ConfigMaster.YAML.readObject(ownerSerializer.serializer(Serialized.class), persist);
            shares = SHARE_INFO_SHAPE.createValued(db.shares);
            shareFileIdx.set(db.shareFileIndex);
        } else {
            shares = SHARE_INFO_SHAPE.create();
        }
    }

    private void removeShare(ShareInfo info) {
        synchronized (lock) {
            if (!shares.remove(info)) return;
            markDirty();
        }
        getLogger().info("移除了直链/share/{}", info);
    }

    @Override
    protected void onDisable() {
        save();
    }

    private ScheduleTask saveLater;
    private void markDirty() {
        dirty = true;
        if (saveLater != null) saveLater.cancel();
        saveLater = getScheduler().delay(this::save, 30000);
    }

    public void save() {
        Serialized snapshot;
        synchronized (lock) {
            if (!dirty) return;
            snapshot = new Serialized(this);
            dirty = false;
        }

        try {
            IOUtil.writeFileEvenMoreSafe(getDataFolder(), "db.yml", file -> {
                try {
                    ConfigMaster.YAML.writeObject(ownerSerializer.serializer(Serialized.class), snapshot, file);
                    getLogger().debug("配置已保存");
                } catch (IOException e) {
                    Helpers.athrow(e);
                }
            });
        } catch (IOException e) {
            markDirty();
            getLogger().error("配置文件保存失败", e);
        }
    }

    private ShareInfo getUnexpired(Request req) {
        ShareInfo info = shares.get(req.argument("shareId"));
        if (info != null && info.isExpired()) {
            synchronized (lock) {
                if (!shares.remove(info)) return null;
                markDirty();
            }
            deleteShare(info);
            return null;
        }

        return info;
    }

    //region Get
    @GET(":shareId")
    public Response index(Request req) {
        if (!req.absolutePath().endsWith("/")) return Response.redirect(req, "/"+req.absolutePath()+"/");

        var zip = getDescription().getArchive();
        return Response.file(req, new ZipRouter.ZipFileInfo(zip, zip.getEntry("share.html")));
    }

    @Accepts(Accepts.GET)
    @Route(value = ":shareId/explore", prefix = true)
    public Response shareExplore(Request req) throws IOException {
        var share = getUnexpired(req);
        if (share == null) return Response.httpError(404);

		if (share.code != null && !share.code.equals(req.cookie().getOrDefault("code", Cookie.EMPTY).value())) {
			return Response.httpError(403);
		}

        if (share.expireType() == 2) return Response.httpError(404);

        if (share.base == null) return Response.text("未实现虚拟文件系统");
        PathIndexRouter pathIndexRouter = new PathIndexRouter(share.base);
        return pathIndexRouter.response(req, req.server());
    }

    @Accepts(Accepts.GET|Accepts.POST)
    @Route(":shareId/info")
    public CharSequence shareInfo(Request req) throws IllegalRequestException {
        var info = getUnexpired(req);
        if (info == null) return "{\"ok\":false,\"data\":\"你来的太晚，分享已经被取消了\"}";

        ok:
        if (info.code != null) {
            if (checkShareCode(req, info)) break ok;

            if (info.code.isEmpty() || !info.code.equals(req.PostFields().get("code"))) return "{\"ok\":false,\"data\":\"文件被密码保护\",\"exist\":true}";

            String token;
            if (info.expireType() == 2) {
                long timeout = System.currentTimeMillis() + 1800000;
                info.countDown(timeout);
                dirty = true;

                int salt = ThreadLocalRandom.current().nextInt() * 1145141919;
                int hash = XXHash.xxHash32(info.hashCode() ^ salt, IOUtil.getSharedByteBuf().putLong(timeout).array(), 0, 8);
                token = IOUtil.getSharedByteBuf().putInt(salt).putLong(timeout).putInt(hash).base64UrlSafe();
            } else {
                token = info.code;
            }

            req.responseHeader().sendCookieToClient(Collections.singletonList(new Cookie("code").value(token).expires(0)));
        }

        return ConfigMaster.JSON.writeObject(info, new CharList().append("{\"ok\":true,\"data\":")).append('}');
    }

    private boolean checkShareCode(Request req, ShareInfo info) throws IllegalRequestException {
        String cookieCode = req.cookie().getOrDefault("code", Cookie.EMPTY).value();
        if (info.expireType() != 2 && !info.code.isEmpty()) return info.code.equals(cookieCode);

        var token = Base64.decode(cookieCode, IOUtil.getSharedByteBuf(), Base64.B64_URL_SAFE_REV);
        if (token.readableBytes() != 16) return false;

        int salt = token.readInt();
        int hash = XXHash.xxHash32(info.hashCode() ^ salt, token.array(), 4, 8);
        long time = token.readLong();
        int exceptHash = token.readInt();

		return hash == exceptHash && System.currentTimeMillis() < time;
	}

    @GET(":shareId/file/:fileId(\\d+)")
    public Response downloadFile(Request req) throws IllegalRequestException {
        ShareInfo info = getUnexpired(req);
        if (info == null) return Response.httpError(404);

        if (info.code != null && !checkShareCode(req, info)) {
            req.server().code(403);
            return Response.text("提取码不正确");
        }

        List<ShareFile> files = info.files;
        int fileId = Integer.parseInt(req.argument("fileId"));
        if (fileId >= files.size()) return Response.httpError(404);

        var file = files.get(fileId);

        var realFile = file.file;
        if (realFile == null) {
            realFile = file.id != 0
                    ? new File(uploadPath, Integer.toString(file.id, 36))
                    : new File(info.base, file.path + '/' + file.name);
            file.file = realFile;
        }
        return Response.file(req, new DiskFileInfo(realFile));
    }
    //endregion

    @POST(":shareId/delete")
    public CharSequence shareDelete(Request req) {
        ShareInfo info = getUnexpired(req);
        if (info == null || !isOwner(req, info)) return "{\"ok\":false,\"data\":\"不存在或不是你的共享\"}";
        boolean flag;
        synchronized (lock) {
            flag = shares.remove(info);
            if (flag) markDirty();
        }
		if (flag) deleteShare(info);

        return "{\"ok\":true,\"data\":\"已删除\"}";
    }

    private void deleteShare(ShareInfo info) {
        if (info.files != null) {
            for (ShareFile file : info.files) {
                if (file.id == 0) continue;

                var realFile = new File(uploadPath, Integer.toString(file.id, 36));
                if (!realFile.delete()) {
                    getLogger().warn("无法删除文件 {}", realFile);
                    getScheduler().loop(realFile::delete, 1000, 10);
                }
            }
        }
    }

    @GET("list")
    public CharSequence shareList(Request req) {
        var user = getUser(req);
        var simpleSer = new ToJson();
        simpleSer.valueMap();
        simpleSer.key("ok");
        simpleSer.value(true);
        simpleSer.key("data");
        simpleSer.valueList();
        if (user != null) {
            List<ShareInfo> myShares = new SimpleList<>();
            synchronized (lock) {
                for (var info : shares) {
                    if (info.owner == user.getId()) myShares.add(info);
                }
            }
            for (int i = 0; i < myShares.size(); i++) {
                writeInfoHistory(myShares.get(i), simpleSer);
            }
        }
        return simpleSer.getValue();
    }

    private static CharList writeInfoHistory(ShareInfo shareInfo) {
        var simpleSer = new ToJson();
        simpleSer.valueMap();
        simpleSer.key("ok");
        simpleSer.value(true);
        simpleSer.key("data");
        writeInfoHistory(shareInfo, simpleSer);
        return simpleSer.getValue();
    }
    private static void writeInfoHistory(ShareInfo info, ToJson ser) {
        ser.valueMap();
        ser.key("id");
        ser.value(info.id);
        ser.key("name");
        ser.value(info.name);
        if (info.code != null) {
            ser.key("code");
            ser.value(info.code);
        }
        ser.key("time");
        ser.value(info.time);

        String name, type;
        var file = info.files;
        if (file == null) {
            name = "";
            type = "text/";
        } else {
            if (file.isEmpty()) {
                ser.pop();
                return;
            }

            if (file.size() == 1) {
                name = file.get(0).name;
                type = file.get(0).mime;
            } else {
                name = "";
                type = "folder";
            }
        }

        ser.key("size");
        ser.value(info.size);
        ser.key("expire");
        ser.value(info.expire);

        ser.key("file");
        ser.valueMap();
        ser.key("name");
        ser.value(name);
        ser.key("type");
        ser.value(type);
        ser.pop();
        ser.pop();
    }

    //region new
    @Interceptor
    private void newHandler(Request req, PostSetting ps) {
        if (ps != null) ps.postAccept(110000, 1000);
    }

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-_@]+$");
    private static final int MAX_FILES_PER_SHARE = 500;
    @POST("new")
    @Interceptor("newHandler")
    public CharSequence shareCreate(Request req) throws IllegalRequestException {
        Map<String, String> map = req.PostFields();

        var user = getUser(req);
        if (user == null) return error("登录以上传文件");

        String id = map.getOrDefault("id", "").trim();
        if (id.isEmpty()) id = IOUtil.getSharedByteBuf().putLong(System.nanoTime() * 19191810L).base64UrlSafe();
        else if (id.length() < 6 || id.length() > 30 || !ID_PATTERN.matcher(id).matches()) return error("参数错误");

        var info = shares.get(id);
        if (info != null || incompleteShares.containsKey(id)) return error("自定义链接已存在");

        var code = map.getOrDefault("code", "").trim();
        if (code.isEmpty()) code = Integer.toUnsignedString((((int) System.nanoTime() * 114514)&Integer.MAX_VALUE) % 1679616/*pow(36, 4)*/, 36);
        else if (code.length() < 4 || code.length() > 12) return error("参数错误");
        else if (code.equals("none")) code = null;

        info = new ShareInfo();
        info.id = id;
        info.owner = user.getId();
        String name = map.getOrDefault("name", "").trim();
        if (name.length() > 30) name = name.substring(0, 30);
        info.name = name.isEmpty() ? "用户"+user.getName()+"的分享@"+Integer.toString(info.hashCode(), 36) : Escape.htmlEntities(name).toString();
        info.code = code;
        info.time = System.currentTimeMillis();

        var type = map.getOrDefault("type", "file");

        String expire = map.getOrDefault("expire", "1");
        if (TextUtil.isNumber(expire) == 0) {
            info.expire = Long.parseLong(expire);
            if (info.expire < 1000000000000L) {
                if (info.expire <= 0 || info.expire > 10000) return error("参数错误");
                if (code == null) return error("公开分享无法使用计次过期");
            } else {
                long expireTime = info.expire - System.currentTimeMillis();
                if (expireTime < 1800000) return error("过期时间不足半小时");
            }
        } else if (!expire.equals("never")) {
            return error("参数错误");
        }

        if (!type.equals("file")) {
            info.text = map.get("text");
            if (info.text == null) {
                File path = new File(map.getOrDefault("path", ""));
                if (!isReadable(req, path)) return error("没有该目录的读取权限");
                info.fillFromServerPath(path);
            } else {
                info.size = (long) info.text.length() << 1;
            }

            synchronized (lock) {
                if (!shares.add(info)) return error("自定义链接已存在");
                markDirty();
            }
        } else {
            info.files = new SimpleList<>();
            info.uploading = new MyHashSet<>(Hasher.identity());
            synchronized (incompleteShares) {
                if (!incompleteShares.add(info)) return error("自定义链接已存在");
            }
        }

        return writeInfoHistory(info);
    }
    private static String error(String error) {return "{\"ok\":false,\"data\":\""+error+"\"}";}

    @POST(":shareId/add")
    public Response shareNewUploadTask(Request req, String name, @Field(orDefault = "") String relativePath, long size, long lastModified) {
        var shareInfo = incompleteShares.get(req.argument("shareId"));
        if (shareInfo != null) {

            var file = new ShareFile();
            file.name = name;
            if (!relativePath.isEmpty())
                file.path = relativePath;
            file.size = size;
            file.lastModified = lastModified;
            file.mime = MimeType.getMimeType(IOUtil.extensionName(name));

            synchronized (shareInfo) {
                if (shareInfo.files.size() > MAX_FILES_PER_SHARE) return Response.text("{\"ok\":false,\"data\":\"一次分享的文件太多了\"}");
                shareInfo.size += size;
                shareInfo.files.add(file);
            }

            int attachmentId = shareFileIdx.getAndIncrement();
            file.id = attachmentId;

            if (size == 0) {
                file.id = -1;
                return Response.text("{\"ok\":false,\"id\":-1}");
            }

            return uploadManager.newTask(uploadPath, Integer.toString(attachmentId, 36), size, (task, ok) -> {
                var set = shareInfo.uploading;
                if (set != null) {
                    synchronized (shareInfo) {
                        if (ok == null) set.add(task);
                        else set.remove(task);
                    }
                }
            });
        }

        return Response.text("{\"ok\":false,\"data\":\"参数错误\"}");
    }

    @GET("upload/:taskId/status")
    public CharSequence shareUploadFile(Request req) throws IllegalRequestException {return uploadManager.getUploadStatus(req);}

    @POST("upload/:taskId/:fragment(\\d{1,8})")
    @Interceptor("uploadProcessor")
    public void shareUploadFile() {}

    @Interceptor
    public void uploadProcessor(Request req, PostSetting setting) throws IOException {uploadManager.uploadProcessor(req, setting);}

    @POST(":shareId/submit")
    public CharSequence sharePackup(Request req) {
        String id = req.argument("shareId");
        ShareInfo shareInfo;
        synchronized (incompleteShares) {
            shareInfo = incompleteShares.removeKey(id);
        }

        block:
        if (shareInfo != null) {
            synchronized (shareInfo) {
                var set = shareInfo.uploading;
                shareInfo.uploading = null;
                for (var task : set) {
                    try {
                        task.finish(false);
                    } catch (IOException e) {
                        getLogger().warn("中止上传任务失败", e);
                    }
                }
            }

            synchronized (lock) {
                if (!shares.add(shareInfo)) {
                    break block;
                }
                markDirty();
            }

            return writeInfoHistory(shareInfo);
        }

        return "{\"ok\":false}";
    }
    //endregion

    // 权限控制
    @Nullable private PermissionHolder getUser(Request req) {return easySso.ipc(new TypedKey<>("getUser"), req);}
    private boolean isOwner(Request req, ShareInfo info) {var user = getUser(req);return user != null && user.getId() == info.owner;}
    private boolean isReadable(Request req, File path) {var user = getUser(req);return user != null && user.isReadable(path.getAbsolutePath());}
}