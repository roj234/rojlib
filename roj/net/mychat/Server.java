package roj.net.mychat;

import roj.collect.IntMap;
import roj.collect.LinkedMyHashMap;
import roj.collect.SimpleList;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.net.WrappedSocket;
import roj.net.http.Action;
import roj.net.http.HttpServer;
import roj.net.http.WebSockets;
import roj.net.http.serv.*;
import roj.text.UTFCoder;
import roj.util.Helpers;
import roj.util.SleepingBeauty;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class Server extends WebSockets implements Router, Consumer<Server.ChatImpl>, Context {
    private final byte[] secKey;

    public Server() {
        secKey = new SecureRandom().generateSeed(32);

        Set<String> prot = getValidProtocol();
        prot.clear(); prot.add("WSChat");
    }

    @Override
    protected void registerNewWorker(Request req, RequestHandler handle, boolean zip) {
        User u = (User) userMap.get(Integer.parseInt(req.path().substring(4)));
        ChatImpl w = new ChatImpl();
        w.ch = handle.ch;
        w.owner = u;
        if (zip) w.enableZip();

        handle.waitAnd(s -> {
            synchronized (Server.class) {
                try {
                    loop.register(Helpers.cast(w), Helpers.cast(Server.this));
                } catch (Exception e) {
                    Helpers.athrow(e);
                }
            }
        });
    }

    static IntMap<AbstractUser> userMap = new IntMap<>();

    public static void main(String[] args) throws IOException {
        SleepingBeauty.sleep();

        User A = new User();
        A.id = 1;
        A.name = A.username = "A";
        A.desc = "";
        A.face = "att/j.png";
        A.friendsSet.add(2);
        A.joinedGroups.add(1000000);

        User B = new User();
        B.id = 2;
        B.name = B.username = "B";
        B.desc = "";
        B.face = "att/b.png";
        B.friendsSet.add(1);
        B.joinedGroups.add(1000000);

        Group T = new Group();
        T.id = 1000000;
        T.name = "测试群聊";
        T.desc = "测试JSON数据\n<I>调试模式已经开启!!</I>\n456\n789\nfhdsufygifhsd\nPowered by Async/2.1";
        T.face = "att/s.png";
        T.addUser(A);
        T.addUser(B);

        r(A); r(B); r(T);

        Server man = new Server();
        HttpServer server = new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1999), 233, man);
        System.out.println("监听 " + server.getSocket().getLocalSocketAddress());
        man.loop = server.getLoop();
        server.start();
    }

    //static final ZipRouter chatHtml = new ZipRouter(new ZipFile("chat.zip"));

    private static void r(AbstractUser b) {
        userMap.put(b.id, b);
    }

    @Override
    public int postMaxLength(Request req) {
        // 4MB
        if (req.path().startsWith("/upload/")) return 4194304;
        return 1024;
    }

    @Override
    public Response response(WrappedSocket ch, Request request, RequestHandler rh) {
        if (Action.OPTIONS == request.action()) {
            return rh.reply(200)
                     .header("Access-Control-Allow-Headers: MCTK\r\n" +
                     "Access-Control-Allow-Origin: " + request.header("Origin") + "\r\n" +
                     "Access-Control-Max-Age: 864000\r\n" +
                     "Access-Control-Allow-Methods: POST, GET\r\n" +
                     "Access-Control-Allow-Credentials: true").nullResponse();
        }
        System.out.println(request.postData());

        if ("websocket".equals(request.header("Upgrade"))) {
            String session = request.path();
            if (!session.startsWith("/ws/")) {
                return rh.reply(401).nullResponse();
            }
            return switchToWebsocket(request, rh);
        }

        if ("/do.php".equals(request.path())) {
            Map<String, String> $_GET = request.getFields();
            if (!$_GET.isEmpty()) {
                LinkedMyHashMap<String, String> map = (LinkedMyHashMap<String, String>) $_GET;
                Response v = null;
                switch (map.firstEntry().getKey()) {
                    case "friend":
                        v = friendOp(request);
                        break;
                    case "user":
                        v = userOp(request);
                        break;
                    case "space":
                        v = spaceOp(request);
                        break;
                    case "ping":
                        v = new StringResponse("pong");
                        break;
                }
                if (v != null) {
                    rh.reply(200).header("Access-Control-Allow-Origin: *");
                    return v;
                }
            }
        }

        if ("/LICENSE".equals(request.path())) {
            return new StringResponse("null");
        }

        return new StringResponse("参数错误");
    }

    static String pass1 = new UTFCoder().encodeBase64(FileUtil.MD5.digest("123456".getBytes()));
    static {
        pass1 = pass1.substring(0, pass1.indexOf('='))
                     .replace('+', '-').replace('/', '_');
        System.out.println(pass1);
    }

    private Response userOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        CMapping m;
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "info":
                String session = request.header("MCTK");
                if (session == null || session.isEmpty() || session.equals("undefined")) {
                    return new StringResponse("{\"ok\":0,\"err\":\"您还没有登录\"}");
                } else {
                    m = new CMapping();
                    m.put("user", userMap.get(Integer.parseInt(session)).cStore());
                    m.put("protocol", "WSChat");
                    m.put("address", "ws://127.0.0.1:1999/ws/" + session);
                    m.put("ok", 1);
                    CList l = m.getOrCreateList("blocked");
                    l.add(3);
                    return new StringResponse(m.toJSONb());
                }
            case "set_info":
                //friend: 0
                //name: Blotern
                //search: false
                //pass:
                //newpass:
                //desc:
                //face: data:image/png;base64,iVBO...==
                return new StringResponse("{\"ok\":0,\"err\":\"暂不支持\"}");
            case "login":
                Map<String, String> posts = request.postFields();
                if (posts == null) return null;

                String name = posts.get("name");
                String pass = posts.get("pass");
                String code = posts.get("code");
                if ((name.equals("1") || name.equals("2")) && pass1.equals(pass)) {
                    return new StringResponse("{\"ok\":1,\"token\":\"" + name + "\"}");
                } else {
                    return new StringResponse("{\"ok\":0,\"err\":\"用户名或密码错误\"}");
                }
            case "logout":
                session = request.header("MCTK");
                try {
                    User u = (User) userMap.get(Integer.parseInt(session));
                    if (u.worker != null) u.worker.close();
                } catch (Throwable ignored) {}
                return new StringResponse("{\"ok\":1}");
            case "reg":
                return new StringResponse("{\"ok\":0,\"err\":\"暂不开放注册\"}");
            default:
                return null;
        }
    }

    private Response spaceOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "list":
                //String uid = $_REQUEST.get("uid");
                //String off = $_REQUEST.get("off");
                //String len = $_REQUEST.get("len");
                // returns: id,uid,time,text
                return new StringResponse("[]");
            case "add":
                //String text = request.postFields().get("text");
                return new StringResponse("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供空间功能\"}");
            default:
                return null;
        }
    }

    private Response friendOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        CMapping m;
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "search":
                // text: 内容
                // type: 内容类型 UID 名称
                // flag: online=1 group=2 person=3
            case "add":
                // id: id
                // text: 验证消息
            case "remove":
                // id: id
            case "move":
                // id: id
                // group: 移动到的分组
            case "confirm":
                // 确认添加好友
                // id: id
            case "flag":
                // id: id
                // flag: 安全标志位
                // (服务端)online=1
                // no_see_him=2, no_see_me=3, blocked=4, always_offline=5
                return new StringResponse("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供好友列表\"}");
            case "list":
                CList list = new CList();
                CMapping map = new CMapping();
                map.getOrCreateList("测试分组")
                   .add(userMap.get(0).cStore())
                   .add(userMap.get(1).cStore());
                list.add(map);
                list.add(new CList());
                list.add(new CList().add(userMap.get(1000000).cStore()));
                return new StringResponse(list.toShortJSONb());
            default:
                return null;
        }
    }

    static class ChatImpl extends WSChat {
        static Context get = userMap::get;

        public User owner;

        @Override
        protected void init() {
            owner.onLogon(get, this);
            sendAlert("欢迎使用MyChat2 (下称\"软件\")<br/>" +
                              "本软件由Roj234独立开发并依法享有其知识产权<br/>" +
                              "<br/>" +
                              "软件以MIT协议开源,并\"按原样提供\", 不包含任何显式或隐式的担保,<br/>" +
                              "上述担保包括但不限于可销售性, 对于特定情况的适应性和安全性<br/>" +
                              "无论何时，无论是否与软件有直接关联, 无论是否在合同或判决等书面文件中写明<br/>" +
                              "作者与版权拥有者都不为软件造成的直接或间接损失负责<br/>" +
                              "<br/>" +
                              "本窗口一经关闭, 即认为您同意本协议<br/>");

            sendMessage(userMap.get(1000000), new Message(1, "欢迎使用MyChat2!"), false);
        }

        @Override
        protected void onClosed() {
            owner.onLogout(get, this);
        }

        @Override
        protected void message(int to, CharSequence msg) {
            System.out.println("Message to " + to + ": " + msg);
            AbstractUser u = userMap.get(to);
            u.postMessage(get, new Message(owner.id, msg.toString()), false);
        }

        @Override
        protected void requestUserInfo(int id) {
            sendUserInfo(userMap.get(id));
        }

        @Override
        protected void requestHistory(int id, CharSequence filter, int off, int len) {
            AbstractUser u = userMap.get(id);
            if (u instanceof Group && off == 0) {
                Group g = (Group) u;
                SimpleList<Message> msgs = new SimpleList<>();
                g.history.getSome(1, g.history.head(), g.history.tail(), msgs);
                sendHistory(id, g.history.size(), msgs);
                return;
            }

            sendHistory(id, 0, Collections.emptyList());
        }

        @Override
        protected void requestClearHistory(int id, int timeout) {
            System.out.println("请求清除历史纪录");
        }

        @Override
        protected void requestColdHistory(int id) {
            System.out.println("请求冷却历史纪录 " + id);
        }
    }

    @Override
    public void accept(ChatImpl chat) {
        chat.owner.onLogout(this, chat);
    }

    @Override
    public AbstractUser getUser(int id) {
        return userMap.get(id);
    }
}