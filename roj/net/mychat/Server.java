package roj.net.mychat;

import roj.collect.IntMap;
import roj.collect.LinkedMyHashMap;
import roj.concurrent.AsyncTest;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.net.WrappedSocket;
import roj.net.http.Action;
import roj.net.http.Code;
import roj.net.http.HttpServer;
import roj.net.http.WebSockets;
import roj.net.http.serv.Reply;
import roj.net.http.serv.Request;
import roj.net.http.serv.RequestHandler;
import roj.net.http.serv.Router;
import roj.text.UTFCoder;
import roj.util.Helpers;
import roj.util.SleepingBeauty;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class Server extends WebSockets implements Router, Consumer<Server.ChatImpl> {
    public Server() {
        Set<String> prot = getValidProtocol();
        prot.clear(); prot.add("WSChat");
    }

    @Override
    protected void registerNewWorker(RequestHandler handle, boolean zip) {
        WSChat w = new ChatImpl();
        w.ch = handle.ch;
        if (zip) w.enableZip();

        handle.waitAnd(s -> {
            try {
                test(w);
                loop.register(Helpers.cast(w), Helpers.cast(this));
            } catch (Exception e) {
                Helpers.athrow(e);
            }
        });
    }

    private void test(WSChat w) {
        AsyncTest.delay = 0;
        for (int i = 0; i < 10; i++) {
            AsyncTest.sched(t -> {
                w.sendMessage(userMap.get(1000000), randomMessage(), false);
            }, 1000);
        }
    }

    static Random rnd = new Random();
    static Message randomMessage() {
        int user = 1 + rnd.nextInt(3);
        Message m = new Message();
        m.uid = user;
        m.text = msgs[rnd.nextInt(msgs.length)];
        m.time = System.currentTimeMillis();
        return m;
    }
    static String[] msgs = ("css is <br/>awesome.|搞事搞事搞事|我受到了惊吓！|听着伙计，我们走了一些弯路。|怪物虐人|这正是" +
            "这一刻所应该发生的事|这里少了一块砖头|undefined（|按下 'E' 键使用|你们也太容易被打动了|bug里怎么有一个程序？|三明" +
            "治不是三角形的|请开始你的表演|其它管理员已同意|你说什么？|创意无处不在|僵尸猎人必须有狩猎许可。|我已经看到结局了|圣堂" +
            "武士，准备拦截！").split("\\|");

    static IntMap<User> userMap = new IntMap<>();

    public static void main(String[] args) throws IOException {
        SleepingBeauty.sleep();

        Server man = new Server();

        HttpServer server = new HttpServer(new InetSocketAddress(1999), 233, man);
        System.out.println("Listening on " + server.getSocket().getLocalSocketAddress());
        server.run();
    }

    private static void r(User b) {
        userMap.put(b.id, b);
    }

    @Override
    public Reply response(WrappedSocket ch, Request request, RequestHandler handle) throws IOException {
        if (Action.OPTIONS == request.action()) {
            Reply r = new Reply(Code.OK);
            r.getRawHeaders().putAscii("Access-Control-Allow-Headers: x-mc-token\r\n" +
                                       "Access-Control-Allow-Origin: *\r\n");
            return r;
        }

        if ("websocket".equals(request.header("Upgrade"))) {
            String session = request.header("X-MC-Token");
            if (!session.equals("123")) {
                return new Reply(Code.UNAVAILABLE, null);
            }
            return switchToWebsocket(request, handle);
        }

        if ("/do.php".equals(request.path())) {
            Map<String, String> $_GET = request.getFields();
            if (!$_GET.isEmpty()) {
                LinkedMyHashMap<String, String> map = (LinkedMyHashMap<String, String>) $_GET;
                Reply v = null;
                switch (map.firstEntry().getKey()) {
                    case "user":
                        v = userOp(request);
                        break;
                    case "space":
                        v = spaceOp(request);
                        break;
                    case "ping":
                        v = new Reply("pong");
                        break;
                }
                if (v != null) {
                    v.header("Access-Control-Allow-Origin", "*");
                    return v;
                }
            }
        }

        return new Reply("param error");
    }

    static String user1 = "admin",
            pass1 = new UTFCoder().encodeBase64(FileUtil.MD5.digest("12345".getBytes()));
    static {
        pass1 = pass1.substring(0, pass1.indexOf('='));
        System.out.println(pass1);
    }

    private Reply userOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        CMapping m;
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "info":
                String session = request.header("X-MC-Token");
                if (!"12345".equals(session)) {
                    return new Reply("{\"ok\":0,\"err\":\"您还没有登录\"}");
                } else {
                    m = new CMapping();
                    User user = userMap.get(1);
                    user.cStore();
                    m.put("user", user);
                    m.put("protocol", "WSChat");
                    m.put("address", "ws://127.0.0.1:1999");
                    m.put("ok", 1);
                    return new Reply(m.toJSONb());
                }
            case "login":
                Map<String, String> posts = request.postFields();
                if (posts == null) return null;

                String name = posts.get("name");
                String pass = posts.get("pass");
                String code = posts.get("code");
                if (user1.equals(name) && pass1.equals(pass)) {
                    return new Reply("{\"ok\":1,\"token\":\"12345\"}");
                } else {
                    return new Reply("{\"ok\":0,\"err\":\"用户名或密码错误" + pass1 + "\"}");
                }
            case "logout":
                // todo some ...
                return new Reply("{\"ok\":1}");
            case "reg":
                return new Reply("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供注册功能\"}");
            default:
                return null;
        }
    }

    private Reply spaceOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "list":
                //String uid = $_REQUEST.get("uid");
                //String off = $_REQUEST.get("off");
                //String len = $_REQUEST.get("len");
                return new Reply("[]");
            case "add":
                //String text = request.postFields().get("text");
                return new Reply("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供空间功能\"}");
            default:
                return null;
        }
    }

    @Override
    public void accept(ChatImpl w) {
        if (w.user != null) w.user.worker = null;
    }

    static UTFCoder uc;
    static class Account {
        String name, pass;
        ChatImpl worker;
    }

    static class ChatImpl extends WSChat {
        Account user;

        @Override
        public void close() throws IOException {
            if (user != null) user.worker = null;
            super.close();
        }

        @Override
        protected void message(int to, CharSequence msg) {
            System.out.println("Message to " + to + ": " + msg);
        }

        @Override
        protected void requestUserInfo(int id) {
            sendUserInfo(userMap.get(id));
        }
    }
}
