package roj.net.cross;

import roj.collect.IntMap;
import roj.collect.LinkedMyHashMap;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.net.http.Action;
import roj.net.http.HttpServer;
import roj.net.http.WebSockets;
import roj.net.http.serv.*;
import roj.net.mychat.*;
import roj.util.Helpers;
import roj.util.SleepingBeauty;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class EmbeddedWSChat extends WebSockets implements Router {
    public EmbeddedWSChat() {
        Set<String> prot = getValidProtocol();
        prot.clear(); prot.add("WSChat");
    }

    // 非同寻常的结论需要有非同寻常的证据
    @Override
    protected void registerNewWorker(Request req, RequestHandler handle, boolean zip) {
        ChatImpl w = new ChatImpl();
        w.ch = handle.ch;
        if (zip) w.enableZip();

        handle.setPreCloseCallback((rh, ok) -> {
            try {
                ChatImpl old = prev.getAndSet(w);
                if (old != null) old.sendExternalLogout("您已在另一窗口登录");
                loop.register(Helpers.cast(w), null);
            } catch (Exception e) {
                prev.set(null);
                Helpers.athrow(e);
            }
            return true;
        });
    }

    static IntMap<AbstractUser> userMap = new IntMap<>();
    static AtomicReference<ChatImpl> prev = new AtomicReference<>();

    public static void main(String[] args) throws IOException {
        SleepingBeauty.sleep();

        User A = new User();
        A.id = 0;
        A.name = "Async";
        A.username = "喵.";
        A.desc = "";
        A.face = "att/j.png";
        A.flag = User.F_ONLINE;

        User B = new User();
        B.id = 1;
        B.name = B.username = "Blotern";
        B.desc = "";
        B.face = "att/b.png";
        B.flag = User.F_ONLINE;

        User L = new User();
        L.id = 2;
        L.name = "备注测试";
        L.username = "Louis_Quepierts";
        L.desc = "";
        L.face = "att/l.png";
        L.flag = User.F_ONLINE;

        User J = new User();
        J.id = 3;
        J.name = J.username = "LaJi_Ding";
        J.desc = "哈哈哈哈哈哈哈哈哈";
        J.face = "att/j.png";
        J.flag = User.F_ONLINE;

        Group SL = new Group();
        SL.id = 1000000;
        SL.name = "圣灵之家";
        SL.desc = "测试JSON数据\n<I>调试模式已经开启!!</I>\n456\n789\nfhdsufygifhsd\nPowered by Async/2.1";
        SL.face = "att/s.png";
        SL.joinGroup(B);
        SL.joinGroup(J);

        r(A); r(B); r(L); r(J); r(SL);

        EmbeddedWSChat man = new EmbeddedWSChat();
        HttpServer server = new HttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1999), 233, man);
        System.out.println("监听 " + server.getSocket().getLocalSocketAddress());
        man.loop = server.getLoop();
        server.start();
    }

    private static void r(AbstractUser b) {
        userMap.put(b.id, b);
    }

    @Override
    public Response response(Request request, RequestHandler rh) {
        if (Action.OPTIONS == request.action()) {
            rh.reply(200)
              .header("Access-Control-Allow-Headers: MCTK\r\n" +
                          "Access-Control-Allow-Origin: " + request.header("Origin") + "\r\n" +
                          "Access-Control-Max-Age: 864000\r\n" +
                          "Access-Control-Allow-Methods: POST, GET\r\n" +
                          "Access-Control-Allow-Credentials: true");
            return null;
        }

        if ("websocket".equals(request.header("Upgrade"))) {
            String session = request.path();
            if (!session.startsWith("/ws/") || !session.substring(4).equals("0")) {
                rh.reply(401);
                return null;
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
                    rh.header("Access-Control-Allow-Origin", "*");
                    return v;
                }
            }
        }

        if ("/LICENSE".equals(request.path())) {
            return new StringResponse("null");
        }

        return new StringResponse("参数错误");
    }

    private Response userOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        CMapping m;
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "info":
                String session = request.header("X-MC-Token");
                if (!"0".equals(session)) {
                    return new StringResponse("{\"ok\":0,\"err\":\"您还没有登录\"}");
                } else {
                    m = new CMapping();
                    m.put("user", userMap.get(0).put());
                    m.put("protocol", "WSChat");
                    m.put("address", "ws://127.0.0.1:1999/ws/0");
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
                return new StringResponse("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供修改个人资料功能\"}");
            case "login":
                return new StringResponse("{\"ok\":1,\"token\":\"0\"}");
            case "logout":
                return new StringResponse("{\"ok\":1}");
            case "reg":
                return new StringResponse("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供注册功能\"}");
            default:
                return null;
        }
    }

    private Response spaceOp(Request request) {
        Map<String, String> $_REQUEST = request.fields();
        switch ($_REQUEST.getOrDefault("op", "")) {
            case "list":
                return new StringResponse("[]");
            case "add":
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
                   .add(userMap.get(0).put())
                   .add(userMap.get(1).put())
                   .add(userMap.get(2).put())
                   .add(userMap.get(3).put());
                list.add(map);
                list.add(new CList());
                list.add(new CList().add(userMap.get(1000000).put()));
                return new StringResponse(list.toShortJSONb());
            default:
                return null;
        }
    }

    static class ChatImpl extends WSChat {
        @Override
        protected void init() {
            sendAlert("欢迎使用MyChat2 (下称\"软件\")<br/>" +
                              "本软件由Roj234独立开发并依法享有其知识产权<br/>" +
                              "<br/>" +
                              "软件以MIT协议开源,并\"按原样提供\", 不包含任何显式或隐式的担保,<br/>" +
                              "上述担保包括但不限于可销售性, 对于特定情况的适应性和安全性<br/>" +
                              "无论何时，无论是否与软件有直接关联, 无论是否在合同或判决等书面文件中写明<br/>" +
                              "作者与版权拥有者都不为软件造成的直接或间接损失负责<br/>" +
                              "<br/>" +
                              "本窗口一经关闭, 即认为您同意本协议<br/>" +
                              "<a href='http://127.0.0.1:1999/LICENSE' target='_blank' style='color:#f00'>MIT协议</a>");
        }

        @Override
        protected void message(int to, CharSequence msg) {
            System.out.println("Message to " + to + ": " + msg);
        }

        @Override
        protected void requestUserInfo(int id) {
            sendUserInfo(userMap.get(id));
        }

        @Override
        protected void requestHistory(int id, CharSequence filter, int off, int len) {
            Message o = new Message();
            o.uid = 1;
            o.text = "这是一条穿越回来的消息";
            sendHistory(id, 1, Collections.singletonList(o));
        }

        @Override
        protected void requestClearHistory(int id, int timeout) {
            System.out.println("REQ CLEAR");
        }

        @Override
        protected void requestColdHistory(int id) {
            System.out.println("REQ Cold " + id);
        }
    }
}
