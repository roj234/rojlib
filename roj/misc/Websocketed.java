package roj.misc;

import roj.net.http.Code;
import roj.net.http.HttpServer;
import roj.net.http.IllegalRequestException;
import roj.net.http.WebSockets;
import roj.net.http.serv.Request;
import roj.net.http.serv.RequestHandler;
import roj.net.http.serv.Response;
import roj.net.http.serv.Router;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/3/20 22:53
 */
public class Websocketed extends WebSockets implements Router {
    List<String> cmd;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Webscoketed [--port=?] <command-line>");
            return;
        }

        int j;
        int port = 8080;
        // 我觉得这种小东西，不值得为它建哪怕一个class，我说的就是你，joptsimple
        for (j = 0; j < args.length; j++) {
            String arg = args[j];
            if (!arg.startsWith("--")) break;
            int i = arg.indexOf('=');
            switch (i < 0 ? arg : arg.substring(0, i)) {
                case "--port":
                    port = Integer.parseInt(arg.substring(i + 1));
                    break;
            }
        }
        ArrayList<String> cmd = new ArrayList<>(args.length - j);
        while (j < args.length) {
            cmd.add(args[j++]);
        }

        if (cmd.isEmpty()) {
            System.out.println("Webscoketed [--port=?] <command-line>");
            return;
        }

        Websocketed websocketed = new Websocketed();
        websocketed.cmd = cmd;

        HttpServer server = new HttpServer(new InetSocketAddress(InetAddress.getLocalHost(), port), 233, websocketed);
        websocketed.loop = server.getLoop();
        server.start();

        System.out.println("监听 " + server.getSocket().getLocalSocketAddress());
    }

    protected void registerNewWorker(Request req, RequestHandler handle, boolean zip) {
        try {
            Worker w = new CmdWorker(cmd);
            w.ch = handle.ch;
            if (zip) w.enableZip();

            handle.setPreCloseCallback(loopRegisterW(w));
        } catch (IOException e) {
            Helpers.athrow(e);
        }
    }

    @Override
    public void checkHeader(Request req) throws IllegalRequestException {
        if (!"websocket".equals(req.header("Upgrade"))) {
            throw new IllegalRequestException(Code.UPGRADE_REQUIRED, "Websocket required");
        }
    }

    @Override
    public Response response(Request req, RequestHandler rh) throws IOException {
        return switchToWebsocket(req, rh);
    }

    static final class CmdWorker extends Worker {
        Process process;
        ByteBuffer sndBuf, rcvBuf;

        CharsetEncoder sysEnc, utfEnc;
        CharsetDecoder sysDec, utfDec;
        CharBuffer sndCb, rcvCb;
        ByteBuffer sndTmp;

        static final Charset system_cs = Charset.defaultCharset();

        CmdWorker(List<String> cmd) throws IOException {
            process = new ProcessBuilder()
                .command(cmd)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start();

            sndBuf = ByteBuffer.allocate(1024);
            sndBuf.flip();

            rcvBuf = ByteBuffer.allocate(1024);
            rcvBuf.flip();

            if (system_cs != StandardCharsets.UTF_8) {
                sysEnc = system_cs.newEncoder();
                sysDec = system_cs.newDecoder();

                utfEnc = StandardCharsets.UTF_8.newEncoder();
                utfDec = StandardCharsets.UTF_8.newDecoder();

                sndCb = CharBuffer.allocate((int) Math.ceil(sndBuf.capacity() / sysEnc.maxBytesPerChar()));
                sndCb.flip();
                rcvCb = CharBuffer.allocate((int) Math.ceil(rcvBuf.capacity() / sysEnc.maxBytesPerChar()));
                rcvCb.flip();

                sndTmp = ByteBuffer.allocate(1024);
                sndTmp.flip();
            }
            System.out.println("会话 " + Integer.toHexString(hashCode()) + " 开始");
        }

        @Override
        protected void onClosed() {
            System.out.println("会话 " + Integer.toHexString(hashCode()) + " 结束: " + errCode + "@" + errMsg);
            process.destroy();

            sndBuf.flip().flip();
            sndCb.flip().flip();
            rcvCb.flip().flip();
        }

        @Override
        public void tick(int elapsed) throws IOException {
            super.tick(elapsed);

            ByteBuffer sndBuf = this.sndBuf;
            ByteBuffer sndTmp = this.sndTmp;
            CharBuffer sndCb = this.sndCb;

            if (sndBuf.hasRemaining()) {
                if (!send(FRAME_TEXT, sndBuf)) return;
            }

            if (sndCb.hasRemaining()) {
                sndBuf.clear();

                CoderResult r = utfEnc.encode(sndCb, sndBuf, false);
                if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                    throw new UTFDataFormatException("Failed to encode as UTF: " + r);
                }
                sndBuf.flip();

                if (!send(FRAME_TEXT, sndBuf)) return;
                if (sndCb.hasRemaining()) return;
            }

            if (rcvCb.hasRemaining()) {
                sndBuf.clear();

                // 编码
                CoderResult r = sysEnc.encode(rcvCb, sndBuf, false);
                if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                    throw new UTFDataFormatException("Failed to encode: " + r + " of " + system_cs);
                }
                sndBuf.flip();

                // 输出
                process.getOutputStream().write(sndBuf.array(), 0, sndBuf.position());
            }

            InputStream in = process.getInputStream();

            while (in.available() > 0) {
                int len = Math.min(in.available(), sndBuf.capacity());
                len = in.read(sndBuf.array(), 0, len);
                if (len < 0) {
                    error(ERR_OK, "进程已终止");
                    return;
                }
                sndBuf.limit(len).position(0);

                if (sysDec != null) {
                    sndCb.clear();

                    // 处理上次没处理完的
                    CoderResult r = sysDec.decode(sndTmp, sndCb, false);
                    if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                        throw new UTFDataFormatException("Failed to decode: " + r + " of " + system_cs);
                    }

                    // 处理新的
                    r = sysDec.decode(sndBuf, sndCb, false);
                    if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                        throw new UTFDataFormatException("Failed to decode: " + r + " of " + system_cs);
                    }
                    sndCb.flip();

                    // 保存没处理完的
                    sndTmp.clear();
                    sndTmp.put(sndBuf).flip();
                    sndBuf.clear();

                    // TO UTF8
                    r = utfEnc.encode(sndCb, sndBuf, false);
                    if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                        throw new UTFDataFormatException("Failed to encode as UTF: " + r);
                    }
                    sndBuf.flip();
                }
                if (!send(FRAME_TEXT, sndBuf)) break;
            }

            if (in.available() < 0) {
                error(ERR_OK, "进程已终止");
            }
        }

        @Override
        protected void onData(int ph, ByteBuffer in) throws IOException {
            OutputStream out = process.getOutputStream();
            if (in.hasArray() && sysDec == null) {
                out.write(in.array(), in.arrayOffset() + in.position(), in.remaining());
                in.position(in.limit());
            } else if (sysEnc == null) {
                ByteBuffer rcvBuf = this.rcvBuf;

                while (in.hasRemaining()) {
                    int len = Math.min(in.remaining(), rcvBuf.capacity());
                    in.get(rcvBuf.array(), 0, len);
                    out.write(rcvBuf.array(), 0, len);
                }
            } else {
                ByteBuffer rcvBuf = this.rcvBuf;
                CharBuffer rcvCB = this.rcvCb;
                rcvCB.compact();

                // 处理上次没处理完的
                CoderResult r = utfDec.decode(rcvBuf, rcvCB, false);
                if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                    throw new UTFDataFormatException("Failed to decode as UTF: " + r );
                }

                if (!rcvBuf.hasRemaining()) {
                    // 处理新的
                    r = utfDec.decode(in, rcvCB, false);
                    if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                        throw new UTFDataFormatException("Failed to decode as UTF: " + r);
                    }

                    rcvBuf.clear();
                }

                // 编码
                rcvCB.flip();
                r = sysEnc.encode(rcvCB, rcvBuf, false);
                if (r.isError() || r.isMalformed() || r.isUnmappable()) {
                    throw new UTFDataFormatException("Failed to encode: " + r + " of " + system_cs);
                }

                // 输出
                out.write(rcvBuf.array(), 0, rcvBuf.position());

                // 保存没处理完的
                rcvBuf.clear();
                rcvBuf.put(in).flip();
            }
            out.flush();
        }
    }
}
