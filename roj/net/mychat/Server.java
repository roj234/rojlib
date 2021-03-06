package roj.net.mychat;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.concurrent.DualBuffered;
import roj.concurrent.TaskPool;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.serial.ToJson;
import roj.config.word.AbstLexer;
import roj.crypt.Base64;
import roj.crypt.SM3;
import roj.io.IOUtil;
import roj.io.PooledBuf;
import roj.math.MathUtils;
import roj.math.MutableInt;
import roj.net.PlainSocket;
import roj.net.http.Action;
import roj.net.http.HttpServer;
import roj.net.http.WebSockets;
import roj.net.http.serv.*;
import roj.text.ACalendar;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.SleepingBeauty;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class Server extends WebSockets implements Router, Context {
    static final SecureRandom rnd = new SecureRandom();
    byte[] secKey;

    public Server() {
        Set<String> prot = getValidProtocol();
        prot.clear(); prot.add("WSChat");
        secKey = new byte[16];
        rnd.nextBytes(secKey);
    }

    @Override
    protected void registerNewWorker(Request req, RequestHandler handle, boolean zip) {
        ChatImpl w = new ChatImpl();
        w.ch = handle.ch;
        w.owner = (User) userMap.get((int) req.ctx().get("id"));
        if (zip) w.enableZip();

        handle.setPreCloseCallback(loopRegisterW(w));
    }

    static File headDir, attDir, imgDir;
    static ChatDAO dao;
    static IntMap<AbstractUser> userMap = new IntMap<>();
    static final TaskPool POOL          = new TaskPool(1, 4, 1, 60000, "Processor");
    static TimeCounter    sharedCounter = new TimeCounter(60000);

    public static void main(String[] args) throws IOException {
        File path = new File("mychat");
        dao = new ChatDAO(path);

        headDir = new File(path, "head");
        if (!headDir.isDirectory() && !headDir.mkdirs()) {
            throw new IOException("Failed to create head directory");
        }

        attDir = new File(path, "att");
        if (!attDir.isDirectory() && !attDir.mkdirs()) {
            throw new IOException("Failed to create attachment directory");
        }

        imgDir = new File(path, "img");
        if (!imgDir.isDirectory() && !imgDir.mkdirs()) {
            throw new IOException("Failed to create image directory");
        }

        FileResponse.loadMimeMap(IOUtil.readUTF(new File("mychat/mime.ini")));

        SleepingBeauty.sleep();

        User S = new User();
        S.name = "??????";
        S.desc = "??????????????????,???UID???0";
        S.face = "http://127.0.0.1:1999/head/0";

        User A = new User();
        A.id = 1;
        A.name = A.username = "A";
        A.desc = "";
        A.face = "http://127.0.0.1:1999/head/1";
        A.friends.add(2);
        A.joinedGroups.add(1000000);

        User B = new User();
        B.id = 2;
        B.name = B.username = "B";
        B.desc = "";
        B.face = "http://127.0.0.1:1999/head/2";
        B.friends.add(1);
        B.joinedGroups.add(1000000);

        Group T = new Group();
        T.id = 1000000;
        T.name = "????????????";
        T.desc = "??????JSON??????\n<I>????????????????????????!!</I>\nPowered by Async/2.1";
        T.face = "http://127.0.0.1:1999/head/1000000";
        T.joinGroup(A);
        T.joinGroup(B);

        r(S); r(A); r(B); r(T);

        Server man = new Server();
        HttpServer server = new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1999), 233, man);
        System.out.println("?????? " + server.getSocket().getLocalSocketAddress());
        man.loop = server.getLoop();
        server.start();
    }

    //static final ZipRouter chatHtml = new ZipRouter(new ZipFile("chat.zip"));

    private static void r(AbstractUser b) {
        userMap.put(b.id, b);
    }

    @Override
    public Response errorCaught(Throwable e, RequestHandler rh, String where) {
        e.printStackTrace();
        if (where.equals("PARSING_REQUEST")) {
            return rh.reply(500).connClose().returns(StringResponse.forError(0, e));
        } else {
            return rh.reply(500).returns(StringResponse.httpErr(500));
        }
    }

    @Override
    public long postMaxLength(Request req) {
        switch (req.path()) {
            case "/user/login":
            case "/user/reg":
            case "/user/captcha":
                return 512;
        }
        int uid = verifyHash(req);
        if (uid < 0) {
            jsonErrorPre(req, "?????????");
            return 0;
        }

        if (req.path().startsWith("/att/upload/") ||
            req.path().startsWith("/img/upload/")) {
            User u = (User) userMap.get(uid);
            if (u.largeConn.incrementAndGet() > 4) {
                u.largeConn.decrementAndGet();
                jsonErrorPre(req, "????????????");
                return 0;
            }

            int fileCount = MathUtils.parseInt(req.path().substring(12));
            boolean img = req.path().startsWith("/img/");
            req.handler().setPostHandler(new UploadHandler(req, fileCount, uid, img));
            return img ? 4194304 : 16777216;
        }

        switch (req.path()) {
            case "/user/set_info":
                return 131072;
            case "/space/add":
                return 65536;
            case "/friend/add":
                return 1024;
            default:
                return 128;
        }
    }

    private static void jsonErrorPre(Request req, String str) {
        req.handler().connClose().reply(200).header("Access-Control-Allow-Origin: *").setReply(jsonErr(str));
    }

    @Override
    public Response response(Request req, RequestHandler rh) {
        if (CorsUtil.isPreflightRequest(req)) {
            return rh.reply(200)
                     .header("Access-Control-Allow-Headers: MCTK\r\n" +
                     "Access-Control-Allow-Origin: " + req.header("Origin") + "\r\n" +
                     "Access-Control-Max-Age: 2592000\r\n" +
                     "Access-Control-Allow-Methods: *").returnNull();
        }

        // Strict-Transport-Security: max-age=1000; includeSubDomains

        // IM
        if ("websocket".equals(req.header("Upgrade"))) {
            int id = verifyHash(req.path().substring(1), rh);
            if (id < 0) return rh.reply(403).returnNull();

            req.ctx().put("id", id);
            return switchToWebsocket(req, rh);
        }

        List<String> lst = getRestfulUrl(req);
        if (lst.isEmpty()) return rh.reply(403).returnNull();

        int uid = verifyHash(req);

        Response v = null;
        switch (lst.get(0)) {
            case "friend":
                if (lst.size() < 2) break;
                if (uid < 0) break;

                v = friendOp(req, lst);
                break;
            case "user":
                if (lst.size() < 2) break;

                v = userOp(req, lst, uid);
                break;
            case "space":
                if (lst.size() < 2) break;

                v = spaceOp(req, lst, uid);
                break;
            case "ping":
                v = new StringResponse("pong");
                break;
            // standard image
            case "img": {
                if (lst.size() < 2) break;

                User u = (User) userMap.get(uid);

                if (lst.get(1).equals("upload")) {
                    u.largeConn.decrementAndGet();
                    v = doUpload(req);
                    break;
                }

                TimeCounter tc = u == null ? sharedCounter : u.imgCounter;

                // ??????10?????????
                if (tc.plus() > 10) {
                    v = jsonErr("????????????");
                    break;
                }

                File file = new File(imgDir, pathFilter(req.path().substring(5)));
                if (!file.isFile())
                    return rh.reply(404).returns(StringResponse.httpErr(404));

                v = new ChunkedFile(file).response(req, rh);
                //?????? 'Vary: Origin' ?????????????????????Origin???????????????????????????
                rh.header("Access-Control-Allow-Origin: *");
                return v;
            }
            // head image
            case "head": {
                File img = new File(headDir, pathFilter(req.path().substring(6)));
                if (!img.isFile()) img = new File(headDir, "default");
                if (!img.isFile()) throw new IllegalArgumentException("??????: ??????????????????");
                v = new ChunkedFile(img).response(req, rh);
                rh.header("Access-Control-Allow-Origin: *");
                return v;
            }
            // attachment
            case "att": {
                if (lst.size() < 2) break;

                User u = (User) userMap.get(uid);

                if (lst.get(1).equals("upload")) {
                    if (u == null) break;
                    u.largeConn.decrementAndGet();
                    v = doUpload(req);
                    break;
                } else if (lst.get(1).equals("token")) {
                    if (u == null) break;
                    // bit2: attachment, expire: 30 minutes
                    return new StringResponse("{\"ok\":1,\"code\":\"" + token(u, 1, 180000) + "\"}");
                }

                if (lst.size() < 4) break;

                // /att/[attachment name]/[expire time]/[hash]

                File file = new File(attDir, lst.get(1));
                if (!file.isFile())
                    return rh.reply(404).returns(StringResponse.httpErr(404));

                if (req.action() == Action.DELETE) {
                    UTFCoder uc = IOUtil.SharedCoder.get();
                    ByteList bb = uc.decodeBase64R(file.getName());
                    if (bb.wIndex() < 4 || bb.readInt(bb.wIndex() - 4) != uid)
                        v = jsonErr("????????????");
                    else {
                        int i = 10;
                        while (!file.delete()) {
                            if (i-- == 0) {
                                v = jsonErr("????????????");
                                break;
                            }
                        }
                        if (v == null)
                            v = new StringResponse("{\"ok\":1}");
                    }
                    break;
                }

                // ??????5????????? / ??????4?????????
                if (u.largeConn.get() > 4 ||
                    (u.attachCounter.plus() > 5 && file.length() > 2048)) {
                    v = jsonErr("????????????,???????????????");
                    //Retry-After: 120
                    break;
                }
                u.largeConn.getAndIncrement();
                req.handler().setPreCloseCallback((rh1, normalFinish) -> {
                    u.largeConn.decrementAndGet();
                    return false;
                });
                v = new ChunkedFile(file).response(req, rh);
                if (!req.header("Origin").isEmpty())
                    rh.header("Access-Control-Allow-Origin: *");
                return v;
            }
        }

        if (v != null) {
            if (!req.header("Origin").isEmpty())
                rh.reply(200).header("Access-Control-Allow-Origin: *");
            return v;
        }

        return rh.reply(403).returns(StringResponse.httpErr(403));
    }

    private static String pathFilter(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!TextUtil.isAsciiDisplayChar(c) || c == '/' || c == '\\')
                return "invalid";
        }
        return path;
    }

    private static Response doUpload(Request req) {
        UploadHandler ph = (UploadHandler) req.ctx().get(Request.CTX_POST_HANDLER);
        if (ph == null) return null;

        ToJson ser = new ToJson();
        ser.valueList();

        File[] files = ph.files;
        String[] errors = ph.errors;
        for (int i = 0; i < files.length; i++) {
            ser.valueMap();

            File f = files[i];
            boolean ok = errors == null || errors[i] == null;
            if (ok) files[i] = null;

            ser.key("ok");
            ser.value(ok);

            ser.key("v");
            ser.value(ok ? f.getName() : errors[i]);

            ser.pop();
        }
        return new StringResponse(ser.getValue());
    }

    private Response userOp(Request req, List<String> lst, int uid) {
        switch (lst.get(1)) {
            case "info":
                if (uid < 0) return jsonErr("?????????");

                CMapping m = new CMapping();
                m.put("user", userMap.get(uid).put());
                m.put("protocol", "WSChat");
                m.put("address", "ws://127.0.0.1:1999/" + req.header("MCTK"));
                m.put("ok", 1);
                return new StringResponse(m.toShortJSONb());
            case "set_info":
                if (uid < 0) return jsonErr("?????????");

                Map<String, ?> x = req.payloadFields();
                String newpass = x.getOrDefault("newpass", Helpers.cast("")).toString();
                ChatDAO.Result rs = dao.changePassword(uid, x.get("pass").toString(), newpass.isEmpty() ? null : newpass);
                if (rs.error != null) return jsonErr(rs.error);

                ByteList face = (ByteList) x.get("face");
                if (face != null) {
                    try {
                        BufferedImage image = ImageIO.read(face.asInputStream());
                        if (image != null)
                            ImageIO.write(image, "PNG", new File(headDir, Integer.toString(uid)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (!x.containsKey("flag") || !x.containsKey("desc") || !x.containsKey("name"))
                    return jsonErr("?????????!");
                User u = (User) userMap.get(uid);
                u.username = x.get("name").toString();
                u.desc = x.get("desc").toString();
                u.flag2 = MathUtils.parseInt(x.get("flag").toString());
                u.onDataChanged(this);

                rs = dao.setUserData(u);
                return rs.error == null ? new StringResponse("{\"ok\":1}") : jsonErr(rs.error);
            case "code":
                return new StringResponse("{\"ok\":1,\"code\":\"" + token(null, 1, 300000) + "\"}");
            case "login":
                Map<String, String> posts = req.payloadFields();
                if (posts == null) return jsonErr("????????????");

                String name = posts.get("name");
                String pass = posts.get("pass");
                String challenge = posts.get("chag");
                String code = posts.get("code");
                rs = dao.login(name, pass, challenge);
                if (rs.error != null) {
                    return jsonErr(rs.error);
                }
                u = (User) userMap.get(rs.uid);

                if (u.worker != null) {
                    u.worker.sendExternalLogout("?????????????????????, ???????????????????????????...<br />" +
                    "??????????????????????????????????????????... <br />" +
                    "??????IP??????: " + req.handler().ch.socket().getRemoteSocketAddress() + "<br />" +
                    "UA: " + req.header("User-Agent") + "<br />" +
                    "??????: " + new ACalendar().formatDate("Y-m-d H:i:s.x", System.currentTimeMillis()));
                }

                rnd.nextBytes(u.salt);
                String hash = hash(u, req.handler());
                return new StringResponse("{\"ok\":1,\"token\":\"" + rs.uid + '-' + hash + "\"}");
            case "logout":
                if (uid >= 0) {
                    u = (User) userMap.get(uid);
                    u.salt[0]++;
                    try {
                        if (u.worker != null) u.worker.close();
                    } catch (Throwable ignored) {}
                }
                return new StringResponse("{\"ok\":1}");
            case "reg":
                posts = req.payloadFields();
                if (posts == null) return jsonErr("????????????");

                name = posts.get("name");
                pass = posts.get("pass");
                rs = dao.register(name, pass);
                if (rs.error == null) {
                    return new StringResponse("{\"ok\":1}");
                } else {
                    return jsonErr(rs.error);
                }
            default:
                return null;
        }
    }

    private DualBuffered<List<SpaceEntry>, RingBuffer<SpaceEntry>> spaceAtThisTime =
            new DualBuffered<List<SpaceEntry>, RingBuffer<SpaceEntry>>(
                    new SimpleList<>(),
                    new RingBuffer<>(100, false)
            ) {
        @Override
        protected void move() {
            List<SpaceEntry> w = this.w;
            RingBuffer<SpaceEntry> r = this.r;

            for (int i = 0; i < w.size(); i++) {
                r.addLast(w.get(i));
            }
            w.clear();
        }
    };

    private Response spaceOp(Request req, List<String> lst, int uid) {
        Map<String, String> $_REQUEST = req.fields();
        switch (lst.get(1)) {
            case "list":
                int[] num = (int[]) req.threadLocalCtx().get("IA");
                num[0] = 10;

                int uid1;
                if (lst.size() > 2) {
                    if (!MathUtils.parseIntOptional(lst.get(2), num))
                        return jsonErr("????????????");
                    uid1 = num[0];
                } else {
                    uid1 = -1;
                }

                num[0] = 10;
                if (!MathUtils.parseIntOptional($_REQUEST.get("off"), num))
                    return jsonErr("????????????");
                int off1 = num[0];

                num[0] = 10;
                if (!MathUtils.parseIntOptional($_REQUEST.get("len"), num))
                    return jsonErr("????????????");
                int len1 = num[0];

                req.handler().setChunked();
                return new ChunkedSpaceResp(off1, len1, uid1);
            case "add":
                if (uid < 0) return jsonErr("?????????");

                SpaceEntry entry = new SpaceEntry();
                entry.text = $_REQUEST.get("text");
                entry.time = System.currentTimeMillis();
                entry.uid = uid;
                entry.id = 1;

                List<SpaceEntry> entries = spaceAtThisTime.forWrite();
                entries.add(entry);
                spaceAtThisTime.writeFinish();

                return new StringResponse("{\"ok\":1}");
            case "del":
                // post: id
                return jsonErr("????????????");
            default:
                return null;
        }
    }

    private Response friendOp(Request request, List<String> lst) {
        switch (lst.get(1)) {
            case "search":
                // text: ??????
                // type: ???????????? UID ??????
                // flag: online=1 group=2 person=3
            case "add":
                // id: id
                // text: ????????????
            case "remove":
                // id: id
            case "move":
                // id: id
                // group: ??????????????????
            case "confirm":
                // ??????????????????
                // id: id
            case "flag":
                // id: id
                // flag: ???????????????
                // (?????????)online=1
                // no_see_him=2, no_see_me=3, blocked=4, always_offline=5
                return new StringResponse("{\"ok\":0,\"err\":\"EmbeddedWSChat?????????????????????\"}");
            case "list":
                ToJson ser = new ToJson();
                CList list = new CList();
                CMapping map = new CMapping();
                map.getOrCreateList("????????????")
                   .add(userMap.get(0).put())
                   .add(userMap.get(1).put());
                list.add(map);
                list.add(new CList());
                list.add(new CList().add(userMap.get(1000000).put()));
                return new StringResponse(list.toShortJSONb());
            default:
                return null;
        }
    }

    // region Misc

    private static Response jsonErr(String s) {
        return new StringResponse("{\"ok\":0,\"err\":\"" + AbstLexer.addSlashes(s) + "\"}", "application/json");
    }

    private boolean verifyToken(User u, int action, String token) {
        UTFCoder uc = IOUtil.SharedCoder.get();
        ByteList bb = uc.decodeBase64R(token);
        // action not same
        if ((action & bb.readInt()) == 0) return false;
        // expired
        if (System.currentTimeMillis() > bb.readLong()) return false;

        SM3 sm3 = (SM3) initLocal().get("SM3");
        sm3.reset();

        byte[] secKey = u == null ? this.secKey : u.salt;
        byte[] d0 = bb.list;
        for (int i = 0; i < 10; i++) {
            sm3.update(secKey);
            sm3.update(d0, 0, 20);
        }

        int ne = 0;
        byte[] d1 = sm3.digest();
        for (int i = 0; i < 32; i++) {
            ne |= d1[i] ^ d0[20 + i];
        }
        return ne == 0;
    }

    // action(usage) | timestamp | random | hash
    private String token(User u, int action, int expire) {
        UTFCoder uc = IOUtil.SharedCoder.get();
        ByteList bb = uc.byteBuf;
        bb.clear();
        bb.putInt(action)
          .putLong(expire < 0 ? -expire : System.currentTimeMillis() + expire)
          .putLong(rnd.nextLong());

        SM3 sm3 = (SM3) initLocal().get("SM3");
        sm3.reset();

        byte[] secKey = u == null ? this.secKey : u.salt;
        for (int i = 0; i < 10; i++) {
            sm3.update(secKey);
            sm3.update(bb.list, 0, 20);
        }

        return uc.encodeBase64(bb.put(sm3.digest()));
    }

    private static int verifyHash(Request req) {
        return verifyHash(req.header("MCTK"), req.handler());
    }

    private static int verifyHash(String hash, RequestHandler rh) {
        if (hash.isEmpty()) return -1;
        int i = hash.indexOf('-');
        Map<String, Object> L = initLocal();

        int[] num = (int[]) L.get("IA");
        num[0] = 10;

        return i > 0 &&
                MathUtils.parseIntOptional(hash.substring(0, i), num) &&
                userMap.get(num[0]) instanceof User &&
                TextUtil.safeEquals(hash((User) userMap.get(num[0]), rh), hash.substring(i+1)) ? num[0] : -1;
    }

    private static String hash(User u, RequestHandler rh) {
        UTFCoder uc = IOUtil.SharedCoder.get();
        uc.baseChars = Base64.B64_URL_SAFE;

        Map<String, Object> L = initLocal();

        SM3 sm3 = (SM3) L.get("SM3");
        sm3.reset();

        sm3.update(u.salt);

        ByteList b = uc.encodeR(u.name);
        sm3.update(b.list, 0, b.wIndex());

        sm3.update(u.salt);

        uc.encodeR(rh.ch.socket().getInetAddress().toString());
        sm3.update(b.list, 0, b.wIndex());

        return uc.encodeBase64(sm3.digest());
    }

    private static MyHashMap<String, Object> initLocal() {
        MyHashMap<String, Object> L = RequestHandler.LocalShared.get().ctx;
        if (!L.containsKey("LST")) {
            L.put("SM3", new SM3());
            L.put("IA", new int[1]);
            L.put("LST", new SimpleList<>());
        }
        return L;
    }

    private static List<String> getRestfulUrl(Request req) {
        Map<String, Object> ctx = initLocal();

        List<String> lst = Helpers.cast(ctx.get("LST"));
        lst.clear();

        TextUtil.split(lst, req.path().substring(1), '/', 10, true);
        return lst;
    }

    // endregion

    static class ChatImpl extends WSChat {
        static Context get = userMap::get;

        public User owner;

        @Override
        protected void init() {
            owner.onLogon(get, this);
            AbstractUser g = userMap.get(1000000);

            sendMessage(g, new Message(Message.STYLE_SUCCESS, "????????????MyChat2!"), true);
            sendMessage(g, new Message(Message.STYLE_WARNING | Message.STYLE_BAR, "????????????MyChat2!"), true);
            sendMessage(g, new Message(Message.STYLE_ERROR, "????????????MyChat2!"), true);
            sendMessage(g, new Message(0, "????????????MyChat2 (??????\"??????\")\n" +
                    "????????????Roj234??????????????????????????????????????????\n\n" +
                    "?????????MIT????????????,???\"???????????????\", ???????????????????????????????????????,\n" +
                    "??????????????????????????????????????????, ??????????????????????????????????????????\n" +
                    "???????????????????????????????????????????????????, ??????????????????????????????????????????????????????\n" +
                    "???????????????????????????????????????????????????????????????????????????\n\n" +
                    "[c:red]?????????????????????, ????????????????????????????????????[/c]"), false);
        }

        @Override
        protected void onClosed() {
            owner.onLogout(get, this);
        }

        @Override
        protected void message(int to, CharSequence msg) {
            AbstractUser u = userMap.get(to);
            if (u == null) {
                sendExternalLogout("???????????????: ?????????????????? " + to);
                return;
            }
            u.postMessage(get, new Message(owner.id, msg.toString()), false);
        }

        @Override
        protected void requestUserInfo(int id) {
            AbstractUser u = userMap.get(id);
            if (u == null) {
                sendExternalLogout("???????????????: ?????????????????? " + id);
                return;
            }
            sendUserInfo(u);
        }

        @Override
        protected void requestHistory(int id, CharSequence filter, int off, int len) {
            AbstractUser u = userMap.get(id);
            if (u instanceof Group) {
                Group g = (Group) u;
                RingBuffer<Message> his = g.history;

                if (len == 0) {
                    sendHistory(id, his.size(), Collections.emptyList());
                    return;
                } else if (len > 100)
                    len = 100;

                SimpleList<Message> msgs = new SimpleList<>(Math.min(his.capacity(), len));

                off = his.size()-len-off;
                if (off < 0) {
                    len += off;
                    off = 0;
                }
                his.getSome(1, his.head(), his.tail(), msgs, off, len);

                filter = filter.toString();
                if (filter.length() > 0) {
                    for (int i = msgs.size() - 1; i >= 0; i--) {
                        if (!msgs.get(i).text.contains(filter)) {
                            msgs.remove(i);
                        }
                    }
                }
                sendHistory(id, his.size(), msgs);
            } else {
                if (len == 0) {
                    sendHistory(id, dao.getHistoryCount(id), Collections.emptyList());
                    return;
                }

                MutableInt mi = new MutableInt();
                List<Message> msg = dao.getHistory(id, filter, off, len, mi);
                sendHistory(id, mi.getValue(), msg);
            }
        }

        @Override
        protected void requestClearHistory(int id, int timeout) {
            AbstractUser u = userMap.get(id);
            if (!(u instanceof User)) return;

            ChatDAO.Result r = dao.delHistory(owner.id, id);
            if (r.error != null) {
                sendAlert("????????????????????????: " + r.error);
            }
        }

        @Override
        protected void requestColdHistory(int id) {
            AbstractUser u = userMap.get(id);
            if (!(u instanceof User)) return;

            System.out.println("???????????????????????? " + id);
        }
    }

    @Override
    public AbstractUser getUser(int id) {
        return userMap.get(id);
    }

    private class ChunkedSpaceResp extends AsyncResponse {
        ToJson ser;
        ByteList shared;
        ByteBuffer tmp;
        Iterator<SpaceEntry> itr;

        public ChunkedSpaceResp(int off1, int len1, int uid1) {
            ser = new ToJson();
            off = off1;
            len = len1;
            uid = uid1;
        }

        @Override
        public void prepare() {
            shared = PooledBuf.alloc().retain();
            ser.reset();
            ser.valueList();
            tmp = PlainSocket.EMPTY;
            itr = spaceAtThisTime.forRead().descendingIterator();
        }

        @Override
        public boolean send(RequestHandler rh) throws IOException {
            if (tmp.hasRemaining()) {
                rh.write(tmp);
                if (tmp.hasRemaining()) return true;
            }

            while (itr.hasNext()) {
                SpaceEntry entry = itr.next();
                if (uid >= 0 && entry.uid != uid) continue;
                if (off-- > 0) continue;
                if (len-- == 0) break;

                entry.serialize(ser);

                shared.clear();
                StringBuilder sb = ser.getHalfValue();
                shared.putUTFData(sb);
                sb.setLength(0);

                rh.write(tmp = ByteBuffer.wrap(shared.list, 0, shared.wIndex()));
                if (tmp.hasRemaining()) return true;
            }

            shared.clear();
            shared.putUTFData(ser.getValue());
            rh.write(tmp = ByteBuffer.wrap(shared.list, 0, shared.wIndex()));
            return tmp.hasRemaining();
        }

        @Override
        public void release() throws IOException {
            spaceAtThisTime.readFinish();
            PooledBuf.alloc().release(shared);
            shared = null;
        }

        int off, len, uid;
    }
}