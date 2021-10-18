/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.net.udp;


import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.SimpleSpinLock;
import roj.concurrent.collect.ConcurrentTimedHashMap;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.CString;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.net.NetworkUtil;
import roj.net.tcp.serv.HttpServer;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.HeadResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.serv.util.Request;
import roj.net.tcp.util.Action;
import roj.net.tcp.util.Code;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Simple DNS Server
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/7/23 20:49
 */
public class DnsServer implements Router {
    static final ConcurrentHashMap<RecordKey, List<Record>> resolvedCache = new ConcurrentHashMap<>();

    MyHashSet<String> blocked = new MyHashSet<>();

    ConcurrentTimedHashMap<XAddr, ForwardQuery> pendingRequest;

    DatagramSocket receiveSocket, serverSocket;

    TimedCleaner cleaner;

    List<InetSocketAddress> trustedForwardDnsServers;
    InetSocketAddress fakeDnsServer;

    public DnsServer(CMapping config, InetSocketAddress address) throws SocketException {
        receiveSocket = new DatagramSocket(new InetSocketAddress(config.getInteger("forwarderReceive")));
        serverSocket = new DatagramSocket(address);
        pendingRequest = new ConcurrentTimedHashMap<>(config.getInteger("requestTimeout"));
        trustedForwardDnsServers = new ArrayList<>();
        CList list = config.getOrCreateList("trustedDnsServers");
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i).asString();
            int j = id.lastIndexOf(':');
            trustedForwardDnsServers.add(
                    new InetSocketAddress(id.substring(0, j), MathUtils.parseInt(id.substring(j + 1))));
        }
        if(!config.getString("fakeDnsServer").isEmpty())
            fakeDnsServer = new InetSocketAddress(config.getString("fakeDnsServer"), 53);

        TimedCleaner cleaner = this.cleaner = new TimedCleaner(this);
        cleaner.start();

        ForwardQueryHandler fwd = new ForwardQueryHandler(this);
        fwd.start();
    }

    // Save / Load

    File cacheFile;
    AtomicBoolean dirty = new AtomicBoolean();
    public void save() throws IOException {
        if (dirty.getAndSet(false) && cacheFile != null) {
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                ByteWriter w = new ByteWriter();
                w.writeInt(0x2a6789fa).writeInt(0);
                int i = 0;
                for (Map.Entry<RecordKey, List<Record>> entry : resolvedCache.entrySet()) {
                    RecordKey key = entry.getKey();
                    w.writeVString(key.url)
                     .writeByte((byte) key.qClass); // u2 if needed

                    key.lock.enqueueReadLock();
                    List<Record> records = entry.getValue();
                    w.writeVarInt(records.size(), false);
                    for (int j = 0; j < records.size(); j++) {
                        Record record = records.get(j);
                        w.writeByte((byte) record.qType) // u2 if needed
                         .writeVarInt(record.data.length, false)
                         .writeBytes(record.data)
                         .writeVarInt(record.TTL, false);
                    }
                    key.lock.releaseReadLock();
                    i++;
                }

                int pos = w.list.pos();
                w.list.pos(4);
                w.writeInt(i);
                w.list.pos(pos);
                w.list.writeToStream(fos);
            }
        }
    }

    public void load() throws IOException {
        assert !cleaner.isAlive();

        if(cacheFile == null || !cacheFile.isFile())
            return;

        ByteReader r = new ByteReader(new ByteList().readStreamArrayFully(new FileInputStream(cacheFile)));
        if(0x2a6789fa != r.readInt()) {
            throw new IOException("File header error");
        }
        resolvedCache.clear();
        int count = r.readInt();
        for (int i = 0; i < count; i++) {
            RecordKey key = new RecordKey();
            key.url = r.readVString();
            key.qClass = r.readByte(); // u2 if needed
            int count2 = r.readVarInt(false);
            List<Record> records = new ArrayList<>(count2);
            for (int j = 0; j < count2; j++) {
                Record e = new Record();
                e.qType = r.readByte(); // u2 if needed
                e.data = r.readBytes(r.readVarInt(false));
                e.TTL = r.readVarInt(false);
                records.add(e);
            }
            resolvedCache.put(key, records);
        }
    }

    public void loadHosts(InputStream in) throws IOException {
        assert !cleaner.isAlive();

        try (SimpleLineReader scan = new SimpleLineReader(in)) {
            for (String line : scan) {
                if(line.isEmpty() || line.startsWith("#"))
                    continue;

                int i = line.indexOf('\t');

                RecordKey key = new RecordKey();
                key.url = line.substring(0, i);
                key.qClass = C_IN;

                Record record = new Record();
                byte[] value = NetworkUtil.ip2bytes(line.substring(i + 1));
                record.qType = value.length == 4 ? Q_A : Q_AAAA;
                record.data = value;
                record.TTL = Integer.MAX_VALUE;

                resolvedCache.computeIfAbsent(key, Helpers.fnArrayList()).add(record);
            }
        }
    }

    public void loadBlocked(InputStream in) throws IOException {
        assert !cleaner.isAlive();

        try (SimpleLineReader scan = new SimpleLineReader(in)) {
            for (String ln : scan) {
                if(ln.isEmpty() || ln.startsWith("!"))
                    continue;

                blocked.add(ln);
            }
        }
    }

    public String dumpIpAddress() {
        return TextUtil.prettyPrint(resolvedCache.entrySet());
    }

    // Utility classes

    static final class XAddr {
        InetAddress addr;
        char port, id;

        public XAddr() {}

        public XAddr(DatagramPacket packet) {
            init(packet);
        }

        XAddr init(DatagramPacket packet) {
            this.addr = packet.getAddress();
            this.port = (char) packet.getPort();
            byte[] buf = packet.getData();
            this.id = (char) (((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF));
            return this;
        }

        public XAddr(InetSocketAddress socketAddress, char sessionId) {
            this.addr = socketAddress.getAddress();
            this.port = (char) socketAddress.getPort();
            this.id = sessionId;
        }

        public XAddr copy() {
            XAddr n = new XAddr();
            n.addr = addr;
            n.port = port;
            n.id = id;
            return n;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            XAddr xAddr = (XAddr) o;

            if (port != xAddr.port) return false;
            if (id != xAddr.id) return false;
            return addr.equals(xAddr.addr);
        }

        @Override
        public int hashCode() {
            int result = addr.hashCode();
            result = 31 * result + (int) port;
            result = 31 * result + (int) id;
            return result;
        }

        @Override
        public String toString() {
            return String.valueOf(addr) + ':' + (int) port + '#' + (int) id;
        }
    }

    static final class TimedCleaner extends Thread {
        DnsServer server;

        public TimedCleaner(DnsServer server) {
            setName("DNS Forward Query Cleaner");
            setDaemon(true);
            this.server = server;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    server.pendingRequest.clearOutdatedEntry();
                } catch (ConcurrentModificationException ignored) {
                    server.pendingRequest.FORCE_RELEASE_LOCK();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    static final class ForwardQueryHandler extends Thread {
        DnsServer server;

        public ForwardQueryHandler(DnsServer server) {
            setName("DNS Forward Query Handler");
            setDaemon(true);
            this.server = server;
        }

        @Override
        public void run() {
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, 512);
            XAddr xAddr = new XAddr();
            while (true) {
                packet.setLength(512);
                try {
                    server.receiveSocket.receive(packet);

                    ForwardQuery req = server.pendingRequest.remove(xAddr.init(packet));
                    if(req == null) {
                        if(server.trustedForwardDnsServers.contains(packet.getSocketAddress())) {
                            server.maybeRecvData(packet, xAddr);
                        } else {
                            System.out.println("[Warn] 未授权的数据包 " + xAddr);
                        }
                    } else {
                        //System.out.println("[Dbg]接收到来自 " + xAddr + " 的消息");
                        req.handle(server, packet, xAddr);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static final class ForwardQuery {
        int remain;
        MyHashMap<InetSocketAddress, DnsResponse> truncated = new MyHashMap<>(2);
        DnsResponse[] responses;

        DnsQuery query;

        public ForwardQuery(DnsQuery query, int remain) {
            this.remain = remain;
            this.responses = new DnsResponse[remain];
            this.query = query;
        }

        public void handle(DnsServer server, DatagramPacket packet, XAddr xAddr) {
            ByteReader r = new ByteReader(packet.getData());
            r.getBytes().pos(packet.getLength());

            try {
                DnsResponse resp = server.processDnsResponse(packet, r);
                if(remain == 0) {
                    System.out.println("啊我毒了: " + resp);
                    return;
                }

                InetSocketAddress iAddr = (InetSocketAddress) packet.getSocketAddress();
                if(resp.truncated) {
                    System.out.println("[Dbg]TC of " + xAddr);

                    DnsResponse TCResp = truncated.putIfAbsent(iAddr, resp);
                    if(TCResp != null) {
                        TCResp.response.putAll(resp.response);
                    }

                    server.pendingRequest.put(xAddr.copy(), this);
                } else {
                    DnsResponse TCResp = truncated.get(iAddr);
                    if(TCResp != null) {
                        TCResp.response.putAll(resp.response);
                        resp = TCResp;
                        System.out.println("[Dbg]TC end: " + resp);
                    }

                    responses[--remain] = resp;
                    if (remain == 0) {
                        server.processResolvedResponse(this);
                    }
                }
            } catch (Throwable e) {
                System.err.println("[Error]DnsResp process error: ");
                e.printStackTrace();
            }
        }
    }

    static final byte FLAG_NORM = 1;
    static final byte FLAG_A    = 2;
    static final byte FLAG_EX   = 4;

    public static final class RecordKey {
        String url;
        short qClass;
        byte flag;

        public SimpleSpinLock lock = new SimpleSpinLock();

        public String getUrl() {
            return url;
        }

        public short getQueryClass() {
            return qClass;
        }

        public byte getFlag() {
            return flag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RecordKey that = (RecordKey) o;

            if (qClass != that.qClass) return false;
            return url.equals(that.url);
        }

        @Override
        public int hashCode() {
            int result = url.hashCode();
            result = 31 * result + (int) qClass;
            return result;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder().append(url).append('@');
            switch (qClass) {
                case C_CH:
                    sb.append("CHAOS");
                    break;
                case C_HS:
                    sb.append("Hesiod");
                    break;
                case C_IN:
                    sb.append("Internet");
                    break;
            }
            return sb.toString();
        }
    }

    public static final class Record {
        public short qType;
        public byte[] data;
        public int TTL, timestamp = (int) (System.currentTimeMillis() / 1000);

        public static List<Record> iterateFinder(List<Record> records, List<Record> target, short qType, Function<String, List<Record>> getter) {
            for (int i = 0; i < records.size(); i++) {
                Record r = records.get(i);
                if(r.qType == Q_CNAME) {
                    iterateFinder(getter.apply(QDataToString(r.qType, r.data)), target, qType, getter);
                    // alias
                }
            }

            switch (qType) {
                case Q_MAILB: {
                    for (int i = 0; i < records.size(); i++) {
                        Record record = records.get(i);
                        if(record.qType >= 7 && record.qType <= 9) {
                            target.add(record);
                        }
                    }
                    return target;
                }
                case Q_ANY: {
                    target.addAll(records);
                    return target;
                }
            }

            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                if(record.qType == qType) {
                    target.add(record);
                }
            }

            return target;
        }

        static final float TTL_UPDATE_MULTIPLIER = 1 / Float.parseFloat(System.getProperty("TTL_UPDATE_MULTIPLIER", "1"));
        public boolean shouldUpdate(int ts) {
            return (TTL_UPDATE_MULTIPLIER * (float)(ts - timestamp)) >= TTL;
        }

        @Override
        public String toString() {
            return QTypeToString(qType) + " Record{" + (data == null ? null : QDataToString(qType, data)) +
                    ", TTL=" + TTL +
                    '}';
        }

        private static String QTypeToString(short qType) {
            switch (qType) {
                case Q_A:
                    return "A";
                case Q_AAAA:
                    return "AAAA";
                case Q_CNAME:
                    return "CNAME";
                case Q_MX:
                    return "MX";
                case Q_NULL:
                    return "NULL";
                case Q_PTR:
                    return "PTR";
                case Q_TXT:
                    return "TXT";
                case Q_WKS:
                    return "WKS";
            }

            return String.valueOf(qType);
        }

        private static String QDataToString(short qType, byte[] data) {
            ByteReader r = new ByteReader(data);
            switch (qType) {
                case Q_A:
                case Q_AAAA:
                    return NetworkUtil.bytes2ip(data);
                case Q_CNAME:
                case Q_MB:
                case Q_MD:
                case Q_MF:
                case Q_MG:
                case Q_MR:
                case Q_NS:
                case Q_PTR: {
                    CharList sb = new CharList(30);
                    try {
                        _r_domain_name(r, sb);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        break;
                    }
                    return sb.toString();
                }
                case Q_HINFO:
                    try {
                        String CPU = _r_character_string(r);
                        String OS = _r_character_string(r);
                        return "CPU: " + CPU + ", OS: " + OS;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        break;
                    }
                case Q_MX: {
                    int pref = r.readUnsignedShort();
                    CharList sb = new CharList(30).append("Preference: ").append(Integer.toString(pref)).append(", Ex: ");
                    try {
                        _r_domain_name(r, sb);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        break;
                    }
                    return sb.toString();
                }
                case Q_SOA: {
                    CharList sb = new CharList(100).append("Src: ");
                    try {
                        _r_domain_name(r, sb);
                        _r_domain_name(r, sb.append(", Owner: "));
                        sb.append(", ZoneId(SERIAL): ").append(Long.toString(r.readUInt()))
                          .append(", ZoneTtl(REFRESH): ").append(Long.toString(r.readUInt()))
                          .append(", Retry: ").append(Long.toString(r.readUInt()))
                          .append(", Expire: ").append(Long.toString(r.readUInt()))
                          .append(", MinTtlInServer: ").append(Long.toString(r.readUInt()));
                    } catch (Throwable e) {
                        e.printStackTrace();
                        break;
                    }
                    return sb.toString();
                }
                case Q_TXT: {
                    CharList sb = new CharList(100).append("[");
                    try {
                        while (r.remain() > 0) {
                            sb.append(_r_character_string(r)).append(", ");
                        }
                        sb.setIndex(sb.length() - 2);
                        sb.append(']');
                    } catch (Throwable e) {
                        e.printStackTrace();
                        break;
                    }
                    return sb.toString();
                }
                case Q_WKS: {
                    CharList sb = new CharList(32).append("Address: ").append(NetworkUtil.bytes2ipv4(data, 0));
                    r.index = 4;
                    return sb.append(", Proto: ").append(Integer.toString(r.readUnsignedByte()))
                             .append(", BitMap: <HIDDEN>, len = ").append(r.remain()).toString();
                }
                case Q_NULL:
                    break;
            }
            return new ByteList(data).toString();
        }

        /**
         * 解压缩指针
         */
        public void read(ByteReader rx, int len) {
            switch (qType) {
                case Q_CNAME:
                case Q_MB:
                case Q_MD:
                case Q_MF:
                case Q_MG:
                case Q_MR:
                case Q_NS:
                case Q_PTR:
                case Q_MX:
                case Q_SOA:
                    break;
                default:
                    data = rx.readBytes(len);
                    return;
            }

            ByteReader rd = new ByteReader(rx.readBytesDelegated(len));
            ByteWriter wd = new ByteWriter(len);

            switch (qType) {
                case Q_CNAME:
                case Q_MB:
                case Q_MD:
                case Q_MF:
                case Q_MG:
                case Q_MR:
                case Q_NS:
                case Q_PTR:
                    try {
                        CharList sb = new CharList(30);
                        _r_mayindex_dn(rd, rx, sb);
                        _w_domain_name(wd, sb);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    break;
                case Q_MX:
                    try {
                        int pref = rd.readUnsignedShort();
                        CharList sb = new CharList(30);
                        _r_mayindex_dn(rd, rx, sb);
                        _w_domain_name(wd.writeShort(pref), sb);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    break;
                case Q_SOA: {
                    try {
                        CharList sb = new CharList(30);
                        _r_mayindex_dn(rd, rx, sb);
                        _w_domain_name(wd, sb);
                        sb.clear();
                        _r_mayindex_dn(rd, rx, sb);
                        _w_domain_name(wd, sb);
                        wd.writeBytes(rd.getBytes().list, rd.index, len - rd.index);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            data = wd.toByteArray();

        }
    }
    
    /**
     * 由域名获得IPv4地址
     */
    static final short Q_A = 1;
    /**
     * 查询授权的域名服务器
     */
    static final short Q_NS = 2;
    /**
     * mail destination
     * @deprecated Obsolete, use MX
     */
    @Deprecated
    static final short Q_MD = 3;
    /**
     * mail forwarder
     * @deprecated Obsolete, use MX
     */
    @Deprecated
    static final short Q_MF = 4;
    /**
     * 查询规范名称, alias
     */
    static final short Q_CNAME = 5;
    /**
     * Start of authority
     */
    static final short Q_SOA = 6;
    /**
     * mailbox domain name (experimental)
     */
    static final short Q_MB = 7;
    /**
     * mail group member (experimental)
     */
    static final short Q_MG = 8;
    /**
     * mail rename domain name (experimental)
     */
    static final short Q_MR = 9;
    /**
     * null response record (experimental)
     */
    static final short Q_NULL = 10;
    /**
     * Well known service
     */
    static final short Q_WKS = 11;
    /**
     * 把IP地址转换成域名（指针记录，反向查询）
     */
    static final short Q_PTR = 12;
    /**
     * Host information
     */
    static final short Q_HINFO = 13;
    /**
     * Mail information
     */
    static final short Q_MINFO = 14;
    /**
     * 邮件交换记录
     */
    static final short Q_MX = 15;
    static final short Q_TXT = 16;
    /**
     * 由域名获得IPv6地址
     */
    static final short Q_AAAA = 28;
    /**
     * 传送整个区的请求 ??
     */
    static final short Q_AXFR = 252;
    /**
     * related: mailbox (MB, MG or MR)
     */
    static final short Q_MAILB = 253;
    /**
     * related: mail agent
     * @deprecated Obsolete, use MX
     */
    @Deprecated
    static final short Q_MAILA = 254;
    /**
     * 所有记录
     */
    static final short Q_ANY = 255;

    /**
     * Internet
     */
    static final byte C_IN = 1;
    /**
     * CSNET
     */
    @Deprecated
    static final byte C_CS = 2;
    /**
     * CHAOS
     */
    static final byte C_CH = 3;
    /**
     * Hesiod [Dyer 87]
     */
    static final byte C_HS = 4;
    /**
     * ANY
     */
    static final byte C_ANY = (byte) 255;

    public static class DnsQuery {
        char sessionId;
        InetAddress senderIp;
        char senderPort, opcode;
        boolean iterate;

        DnsRecord[] records;

        @Override
        public String toString() {
            return "DnsQuery{" +
                    senderIp + ":" + (int) senderPort +
                    ", op=" + (int)opcode +
                    ", RD=" + iterate +
                    ", " + Arrays.toString(records) +
                    '}';
        }
    }

    public static final class DnsResponse extends DnsQuery {
        boolean authorizedAnswer, truncated;
        byte responseCode;
        MyHashMap<RecordKey, List<Record>> response;

        @Override
        public String toString() {
            return "DnsResponse{" +
                    "sender=" + senderIp + ":" + (short) senderPort +
                    ", op=" + (short)opcode +
                    ", RD=" + iterate +
                    ", AA=" + authorizedAnswer +
                    ", RCode=" + responseCode +
                    ", response=" + response +
                    '}';
        }
    }

    public static final class DnsRecord {
        String url;

        /**
         * 指向消息开头的指针
         */
        short ptr;

        /**
         * 查询类别
         */
        short qType;

        /**
         * 查询分类, 通常为{@link #C_IN}
         */
        short qClass;

        @Override
        public String toString() {
            return url + '@' + qType;
        }
    }

    static final int OFF_FLAGS = 2;
    static final int OFF_RES = 6;
    static final int OFF_ARES = 8;
    static final int OFF_EXRES = 10;

    public void run() {
        ByteList bytes = new ByteList(512);
        DatagramPacket pkt = new DatagramPacket(bytes.list, 0);
        ByteReader r = new ByteReader(bytes);
        ByteWriter w = new ByteWriter(bytes);
        CharList sb = new CharList();
        DnsQuery query = new DnsQuery();

        DatagramSocket server = this.serverSocket;

        while (!Thread.interrupted()) {
            try {
                pkt.setLength(512);
                server.receive(pkt);
                bytes.pos(pkt.getLength());
                r.index = 0;
                r.bitIndex = 0;

                query.sessionId = (char) r.readUnsignedShort();

                /*int qrFlag = r.readBit1();*/
                r.skipBits(1);
                /**
                 * 请求类型，
                 * 0  QUERY  标准查询
                 * 1 IQUERY  反向查询
                 * 2 STATUS  DNS状态请求
                 * 5 UPDATE  DNS域更新请求
                 */
                query.opcode = (char) r.readBit(4);
                r.skipBits(2);
                /*int AA = r.readBit1();
                int TC = r.readBit1();*/
                /**
                 * 如果可行的话，执行递归查询
                 */
                query.iterate = r.readBit1() == 1;
                r.index ++;
                /*int RA = r.readBit1();
                int Z = r.readBit1();
                int AD = r.readBit1();
                int CD = r.readBit1();
                int rcode = r.readBit(4);*/

                query.senderIp = pkt.getAddress();
                query.senderPort = (char) pkt.getPort();

                int numQst = r.readUnsignedShort();

                int numRes = r.readUnsignedShort();
                assert numRes == 0;
                int numARes = r.readUnsignedShort();
                assert numARes == 0;
                int numExRes = r.readUnsignedShort();
                assert numExRes == 0;

                assert r.index == 12;

                DnsRecord[] records = query.records = new DnsRecord[numQst];

                for (int i = 0; i < numQst; i++) {
                    DnsRecord q = records[i] = new DnsRecord();
                    q.ptr = (short) (0xC000 | r.index);

                    _r_domain_name(r, sb);

                    q.url = sb.toString();
                    sb.clear();
                    q.qType = r.readShort();
                    if((q.qClass = r.readShort()) != 1) {
                        System.out.println("[Warn]got qClass " + q.qClass);
                    }
                }

                if(processQuery(w, query, false)) {
                    pkt.setLength(w.list.pos());
                    serverSocket.send(pkt);
                }
            } catch (Exception e) {
                System.err.println("[Warn] " + pkt.getSocketAddress() + " 的无效数据包");
                e.printStackTrace();
            }
        }
    }

    private boolean processQuery(ByteWriter w, DnsQuery query, boolean isResolved) {
        RecordKey key = new RecordKey();
        Function<String, List<Record>> fn = s -> {
            if(blocked.contains(s))
                return Collections.emptyList();
            key.url = s;
            return resolvedCache.getOrDefault(key, Collections.emptyList());
        };

        int sum = 0, sumA = 0, sumEx = 0;
        for (DnsRecord dReq : query.records) {
            if(blocked.contains(dReq.url)) {
                System.out.println("[Dbg]Skip blocked " + dReq.url);
                continue;
            }

            key.url = dReq.url;
            key.qClass = dReq.qClass;

            List<Record> cRecords = resolvedCache.get(key);
            if (cRecords == null) {
                if(!isResolved) {
                    forwardDnsRequest(query, w.list);
                    return false;
                } else {
                    System.out.println("[Warn]Unresolved host after resolve attempts: " + key);
                    setRCode(query, w.list, RCODE_SERVER_ERROR);
                    continue;
                }
            }

            key.lock.enqueueReadLock();
            Record.iterateFinder(cRecords, cRecords = new ArrayList<>(), dReq.qType, fn);
            key.lock.releaseReadLock();

            int ts = (int) (System.currentTimeMillis() / 1000);
            for (int j = 0; j < cRecords.size(); j++) {
                Record cRecord = cRecords.get(j);
                if(cRecord.shouldUpdate(ts)) {
                    if(!isResolved) {
                        forwardDnsRequest(query, w.list);
                        return false;
                    } else {
                        if(cRecord.TTL != 0) {
                            System.out.println("[Warn]这TTL过期有亿点快啊: " + key + ": " + cRecord);
                            setRCode(query, w.list, RCODE_SERVER_ERROR);
                            continue;
                        }
                    }
                }
                w.writeShort(dReq.ptr).writeShort(cRecord.qType).writeShort(dReq.qClass).writeInt(cRecord.TTL)
                 .writeShort(cRecord.data.length).writeBytes(cRecord.data);
            }

            switch (key.flag) {
                case FLAG_A:
                    sumA += cRecords.size();
                    break;
                case FLAG_EX:
                    sumEx += cRecords.size();
                    break;
                case FLAG_NORM:
                default:
                    sum += cRecords.size();
                    break;
            }
        }

        ByteList bl = w.list;
        bl.set(OFF_FLAGS, (byte) (bl.getU(OFF_FLAGS) | 128 | 1));
        //if(query.iterate)
        //    bl.set(OFF_FLAGS + 1, (byte) (bl.getU(OFF_FLAGS + 1) | 128));
        bl.set(OFF_RES, (byte) (sum >> 8));
        bl.set(OFF_RES + 1, (byte) sum);
        bl.set(OFF_ARES, (byte) (sumA >> 8));
        bl.set(OFF_ARES + 1, (byte) sumA);
        bl.set(OFF_EXRES, (byte) (sumEx >> 8));
        bl.set(OFF_EXRES + 1, (byte) sumEx);

        //if (!isResolved) {
        //    System.out.println("[Dbg]缓存中中找到了全部, " + query);
        //}

        return true;
    }

    static final byte RCODE_OK              = 0;
    static final byte RCODE_FORMAT_ERROR    = 1;
    static final byte RCODE_SERVER_ERROR    = 2;
    static final byte RCODE_NAME_ERROR      = 3;
    static final byte RCODE_NOT_IMPLEMENTED = 4;
    static final byte RCODE_REFUSED         = 5;

    /**
     * RCode -- ResponseCode <br>
     * 0  OK <br>
     * 1  格式错误   -- 为请求消息格式错误无法解析该请求 <br>
     * 2  服务器错误 -- 因为内部错误无法解析该请求 <br>
     * 3  名字错误   -- 只在权威域名服务器的响应消息中有效，请求的域不存在 <br>
     * 4  未实现     -- 不支持请求的类型 <br>
     * 5  拒绝      -- 拒绝执行请求的操作 <br>
     * 6 ~ 15 保留
     */
    private static void setRCode(DnsQuery query, ByteList clientRequest, int reason) {
        int type = clientRequest.get(3) & 0xF0;
        clientRequest.set(3, (byte) (type | (reason & 0x0F)));
    }

    public void forwardDnsRequest(DnsQuery query, ByteList clientRequest) {
        //System.out.println("[Dbg]前向请求, " + query);

        List<InetSocketAddress> target = trustedForwardDnsServers;

        DatagramPacket pkt = new DatagramPacket(clientRequest.list, clientRequest.pos());

        if(target == null) {
            System.out.println("[Warn]没有前向DNS");
            setRCode(query, clientRequest, RCODE_SERVER_ERROR);
            clientRequest.set(OFF_FLAGS, (byte) (clientRequest.getU(OFF_FLAGS) | 128));
            pkt.setAddress(query.senderIp);
            pkt.setPort(query.senderPort);
            try {
                serverSocket.send(pkt);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        cleaner.interrupt();
        ForwardQuery request = new ForwardQuery(query, target.size());
        try {
            for (int i = 0; i < target.size(); i++) {
                pkt.setSocketAddress(target.get(i));
                pendingRequest.put(new XAddr(target.get(i), query.sessionId), request);
                receiveSocket.send(pkt);
            }
            if(fakeDnsServer != null) {
                pkt.setSocketAddress(fakeDnsServer);
                pendingRequest.put(new XAddr(fakeDnsServer, query.sessionId), request);
                receiveSocket.send(pkt);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public DnsResponse processDnsResponse(DatagramPacket pkt, ByteReader r) throws UTFDataFormatException {
        DnsResponse resp = new DnsResponse();

        resp.sessionId = (char) r.readUnsignedShort();

        resp.senderIp = pkt.getAddress();
        resp.senderPort = (char) pkt.getPort();

        r.bitIndex = 0;

        r.skipBits(1); // QR
        /**
         * 请求类型
         * 0  QUERY  标准查询
         * 1 IQUERY  反向查询
         * 2 STATUS  DNS状态请求
         * 5 UPDATE  DNS域更新请求
         */
        resp.opcode = (char) r.readBit(4);
        resp.authorizedAnswer = r.readBit1() != 0; // 授权回答
        resp.truncated = r.readBit1() != 0; // TC
        r.skipBits(1); // RD
        resp.iterate = r.readBit1() != 0; // RA
        r.skipBits(3);
        // Z
        // AD // Authorization ??
        // CD // Not-Authorization data ??
        resp.responseCode = (byte) r.readBit(4);

        int numQst = r.readUnsignedShort();
        int numRes = r.readUnsignedShort();
        int numARes = r.readUnsignedShort();
        int numExRes = r.readUnsignedShort();

        assert r.index == 12;

        CharList sb = new CharList();

        DnsRecord[] records = resp.records = new DnsRecord[numQst];
        for (int i = 0; i < numQst; i++) {
            DnsRecord q = records[i] = new DnsRecord();
            q.ptr = (short) (0xC000 | r.index);

            _r_domain_name(r, sb);

            q.url = sb.toString();
            sb.clear();
            q.qType = r.readShort();
            q.qClass = r.readShort();
        }

        MyHashMap<RecordKey, List<Record>> map = resp.response = new MyHashMap<>(numRes + numARes + numExRes);
        gather(r, numRes, sb, map, FLAG_NORM);
        gather(r, numARes, sb, map, FLAG_A);
        gather(r, numExRes, sb, map, FLAG_EX);

        return resp;
    }

    public static void gather(ByteReader r, int num, CharList sb, MyHashMap<RecordKey, List<Record>> map, byte flag) throws UTFDataFormatException {
        for (int i = 0; i < num; i++) {
            _r_mayindex_dn(r, r, sb);

            RecordKey key = new RecordKey();
            key.url = sb.toString();
            sb.clear();
            key.flag = flag;

            Record q = new Record();

            q.qType = r.readShort();
            key.qClass = r.readShort();
            q.TTL = r.readInt();

            q.read(r, r.readUnsignedShort());

            map.computeIfAbsent(key, Helpers.fnArrayList()).add(q);
        }
    }

    void processResolvedResponse(ForwardQuery fq) {
        DnsResponse[] responses = fq.responses;
        //System.out.println("[Info]前向请求完成 " + Arrays.toString(responses));
        DnsResponse response = mergeResponse(responses);
        resolvedCache.putAll(response.response);
        dirty.getAndSet(true);

        ByteWriter w = new ByteWriter(new ByteList(512));
        DnsQuery query = fq.query;
        w.writeShort(query.sessionId)
         .writeShort((1 << 15) | (query.opcode << 12) | (response.authorizedAnswer ? 1 << 11 : 0) | (response.iterate ? 1 << 8 : 0) | RCODE_OK) // flag
         .writeShort(query.records.length).writeShort(0).writeInt(0);

        for (DnsRecord req : query.records) {
            _w_domain_name(w, req.url);
            w.writeShort(req.qType).writeShort(req.qClass);
        }

        processQuery(w, query, true);

        DatagramPacket pkt = new DatagramPacket(w.list.list, w.list.pos());
        pkt.setAddress(query.senderIp);
        pkt.setPort(query.senderPort);

        try {
            serverSocket.send(pkt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DnsResponse mergeResponse(DnsResponse[] responses) {
        return responses[0];
    }

    void maybeRecvData(DatagramPacket pkt, XAddr addr) {
        DnsResponse response;
        try {
            response = processDnsResponse(pkt, new ByteReader(pkt.getData()));
            System.out.println("[Warn] 接受未被处理的数据包 " + addr);
            resolvedCache.putAll(response.response);
        } catch (Throwable e) {
            System.err.println("[Error] 未被处理的'可信'数据包无效 " + addr);
            e.printStackTrace();
        }
    }

    public static void _r_mayindex_dn(ByteReader r, ByteReader rx, CharList sb) throws UTFDataFormatException {
        int len;
        do {
            len = r.readUnsignedByte();
            if((len & 0xC0) != 0) {
                if((len & 0xC0) != 0xC0)
                    throw new RuntimeException("Illegal label length " + len);
                int ri = rx.index;
                rx.index = ((len & ~0xC0) << 8) | r.readUByte();
                _r_mayindex_dn(rx, rx, sb);
                rx.index = ri + (r == rx ? 1:0);
                return;
            }
            sb.append(r.readUTF0(len)).append(".");
        } while (len > 0);
        sb.setIndex(sb.length() - 2);
    }

    public static void _w_domain_name(ByteWriter w, CharSequence sb) {
        int prev = 0, i = 0;
        for (; i < sb.length(); i++) {
            char c = sb.charAt(i);
            assert c < 128;
            if(c == '.') {
                if(i - prev > 63)
                    throw new IllegalArgumentException("Domain length should not larger than 63 characters");
                w.writeByte((byte) (i - prev));
                for (int j = prev; j < i; j++) {
                    w.writeByte((byte) sb.charAt(j));
                }
                prev = i + 1;
            }
        }
        if(i - prev > 63)
            throw new IllegalArgumentException("Domain length should not larger than 63 characters");
        w.writeByte((byte) (i - prev));
        if(i - prev > 0) {
            for (int j = prev; j < i; j++) {
                w.writeByte((byte) sb.charAt(j));
            }
            w.writeByte((byte) 0);
        }
    }

    public static void _r_domain_name(ByteReader r, CharList sb) throws UTFDataFormatException {
        int len;
        do {
            len = r.readUnsignedByte();
            if((len & 0xC0) != 0)
                throw new IllegalArgumentException("Illegal label length " + len);
            sb.append(r.readUTF0(len)).append(".");
        } while (len > 0);
        sb.setIndex(sb.length() - 2);
    }

    public static String _r_character_string(ByteReader r) throws UTFDataFormatException {
        return r.readUTF0(r.readUnsignedByte());
    }

    @Override
    public int readTimeout() {
        return 10000;
    }

    @Override
    public Response response(Socket socket, Request request) throws IOException {
        switch (request.path()) {
            case "/favicon.ico":
                return new Reply(Code.NOT_FOUND, StringResponse.errorResponse(Code.NOT_FOUND, null));
            case "/":
            case "": {
                StringBuilder sb = new StringBuilder().append("<title>AsyncDns 1.0</title><h1>Welcome! <br> Asyncorized_MC 基于DNS的广告屏蔽器 1.0</h1>");

                String msg = request.getFields().get("msg");
                if (msg != null) {
                    sb.append("<div style='background: 0xAA8888; margin: 16px; padding: 16px; border: #000 1px dashed; font-size: 24px; text-align: center;'>")
                      .append(TextUtil.unescapeBytes(msg))
                      .append("</div>");
                }

                sb.append("欢迎您,").append(System.getProperty("user.name", "用户"))
                  .append("! <br/><a href='/stat' style='color:red;'>点我列出缓存的DNS解析</a><br/>" +
                          "<a href='/save'>点我<span style='color:green'>保存</span>缓存的DNS解析</a><br/>" +
                          "<a href='/stop'>点我关闭服务器</a><br/>" +
                          "<h2> 设置或者删除DNS解析: </h2>")
                  .append("<form action='/set' method='post' >Url: <input type='text' name='url' /><br/>" +
                          "Type: <input type='number' name='type' /><br/>" +
                          "Content: <input type='text' name='cnt' /><input type='submit' value='提交' /></form>")
                  .append("<pre>Type: -2 屏蔽\n, -1 删除, \nA(IPV4): " + Q_A + ", \nAAAA(IPV6): " + Q_AAAA + " \nCNAME: " + Q_CNAME + ", \n其他看rfc" + "</pre>")
                  .append("<h2 style='color:#eecc44;margin: 10px auto;'>Powered by Asyncorized_MC's HTTPServer v1.2.0</h2>Allocated Memory: ")
                  .append(TextUtil.getScaledNumber(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

                return new Reply(Code.OK, new StringResponse(sb, "text/html"), request.action());
            }
            case "/stop": {
                System.exit(0);
                return null;
            }
            case "/stat":
                return new Reply(Code.OK, new StringResponse(dumpIpAddress(), "text/plain"));
            case "/save": {
                HeadResponse hp = new HeadResponse();
                hp.headers().put("Location", dirty.get() ? "/?msg=保存成功" : "/?msg=数据没更改");
                save();
                return new Reply(Code.FOUND, hp);
            }
            case "/set": {
                if(request.action() != Action.POST) {
                    return new Reply(Code.METHOD_NOT_ALLOWED, StringResponse.errorResponse(Code.METHOD_NOT_ALLOWED, "不是POST请求"));
                }
                Map<String, String> postFields = request.postFields();
                String url = postFields.get("url");
                String type = postFields.get("type");
                String cnt = postFields.get("cnt");
                String msg = null;
                if(url == null || type == null || cnt == null)
                    msg = "缺field";
                else {
                    RecordKey key = new RecordKey();
                    key.url = url;
                    key.qClass = C_IN;

                    if (type.startsWith("-")) {
                        if(type.equals("-1"))
                            msg = (resolvedCache.remove(key) == null) ? "不存在" : "已清除";
                        else
                            msg = blocked.add(url) ? "屏蔽完成" : "已存在";
                    } else {
                        Record e = new Record();
                        e.TTL = Integer.MAX_VALUE;
                        short qType = (short) MathUtils.parseInt(type);
                        e.qType = qType;
                        if (qType == Q_A || qType == Q_AAAA) {
                            e.data = NetworkUtil.ip2bytes(cnt);
                        } else {
                            switch (qType) {
                                case Q_CNAME:
                                case Q_MB:
                                case Q_MD:
                                case Q_MF:
                                case Q_MG:
                                case Q_MR:
                                case Q_NS:
                                case Q_PTR:
                                    ByteWriter w = new ByteWriter();
                                    _w_domain_name(w, cnt);
                                    e.data = w.toByteArray();
                                    break;
                                default:
                                    msg = "暂不支持" + Record.QTypeToString(qType);
                            }
                        }

                        if(msg == null) {
                            List<Record> records = resolvedCache.computeIfAbsent(key, Helpers.fnArrayList());
                            key.lock.enqueueWriteLock();
                            records.clear();
                            records.add(e);
                            key.lock.releaseWriteLock();
                            msg = "操作完成";
                        }
                    }
                }

                HeadResponse hp = new HeadResponse();
                hp.headers().put("Location", "/?msg=" + msg);
                return new Reply(Code.FOUND, hp);
            }
            default:
                return new Reply(Code.NOT_FOUND, StringResponse.errorResponse(Code.NOT_FOUND, "未定义的路由"));
        }
    }

    @Override
    public int maxLength() {
        return 8192;
    }

    @Override
    public boolean checkAction(int action) {
        return action == Action.POST || action == Action.GET;
    }

    public static void main(String[] args) throws IOException {
        int httpPort = -1, dnsPort = 53;
        String controlIp = "127.0.0.1";
        MyHashSet<InputStream> files = new MyHashSet<>(), blocked = new MyHashSet<>();
        File cache = null;

        CMapping cfg = new CMapping();
        cfg.getOrCreateList("trustedDnsServers").add(new CString("223.5.5.5:53"));
        cfg.put("forwarderReceive", 40000);
        cfg.put("requestTimeout", 5000);

        for(int i = 0; i < args.length; i++) {
            switch(args[i]) {
                case "-auto":
                    httpPort = 8818;
                    blocked.add(DnsServer.class.getClassLoader().getResourceAsStream("META-INF/15_optimized.txt"));
                    blocked.add(DnsServer.class.getClassLoader().getResourceAsStream("META-INF/adguard.txt"));
                    break;
                case "-config":
                    try {
                        cfg = JSONParser.parse(IOUtil.readUTF(new FileInputStream(args[++i]))).asMap();
                    } catch (ClassCastException | ParseException e) {
                        e.printStackTrace();
                        return;
                    }
                    break;
                case "-http":
                    httpPort = Integer.parseInt(args[++i]);
                    break;
                case "-control":
                    controlIp = args[++i].equals("*") ? null : args[i];
                    break;
                case "-cache":
                    cache = new File(args[++i]);
                    break;
                case "-hosts":
                    while (!args[++i].equals("---"))
                        files.add(new FileInputStream(args[i]));
                    break;
                case "-block":
                    while (!args[++i].equals("---"))
                        blocked.add(new FileInputStream(args[i]));
                    break;
                default:
                    throw new IllegalArgumentException("未知 " + args[i]);
            }
        }

        InetAddress addr = controlIp == null ? null : InetAddress.getByAddress(NetworkUtil.ip2bytes(controlIp));

        /**
         * Init DNS Server
         */


        InetSocketAddress address = new InetSocketAddress(addr, dnsPort);
        System.out.println("Dns listening on " + address);
        DnsServer dns = new DnsServer(cfg, address);
        for (InputStream file : files) {
            dns.loadHosts(file);
        }
        for (InputStream file : blocked) {
            dns.loadBlocked(file);
        }
        dns.cacheFile = cache;
        if (cache != null && cache.isFile())
            try {
                dns.load();
            } catch (IOException e) {
                e.printStackTrace();
            }

        /**
         * Run HTTP Server
         */

        if(httpPort != -1) {
            InetSocketAddress ha = new InetSocketAddress(addr, httpPort);
            Thread http = new Thread(new HttpServer(ha, 256, dns));
            http.setDaemon(true);
            http.setName("Http Server");
            http.start();
            System.out.println("Http listening on " + ha);
        }

        /**
         * Auto save
         */

        if(cache != null) {
            Thread saver = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(600 * 1000);
                        dns.save();
                    } catch (InterruptedException | IOException e) {
                        break;
                    }
                }
            });
            saver.setDaemon(true);
            saver.setName("Dns Saver");
            saver.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    dns.save();
                } catch (IOException ignored) {
                }
            }));
        }

        /**
         * Use main thread as DNS Server
         */

        System.out.println("Welcome, to a cleaner world, " + System.getProperty("user.name", "user") + " !\n");
        //roj.misc.CpFilter.registerShutdownHook();

        Thread.currentThread().setName("Dns Server");
        dns.run();
    }
}