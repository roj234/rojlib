/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.misc;

import roj.collect.TrieTreeSet;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.http.HttpConnection;
import roj.net.http.HttpServer;
import roj.net.udp.DnsServer;
import roj.net.udp.DnsServer.Record;
import roj.text.CharList;
import roj.text.SimpleLineReader;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;

/**
 * @author solo6975
 * @since 2022/1/1 19:20
 */
public class AdGuard {
    public static void main(String[] args) throws IOException, ParseException {
        String configFile;
        if (args.length > 0) {
            configFile = args[0];
        } else {
            configFile = "config.json";
        }
        CMapping cfg = JSONParser.parse(IOUtil.readUTF(new File(configFile)), JSONParser.LITERAL_KEY).asMap();

        /**
         * Init DNS Server
         */
        InetSocketAddress local = new InetSocketAddress(InetAddress.getLoopbackAddress(), 53);
        System.out.println("Dns listening on " + local);
        DnsServer dns = new DnsServer(cfg, local);

        CList list = cfg.getOrCreateList("hosts");
        for (int i = 0; i < list.size(); i++) {
            dns.loadHosts(new FileInputStream(list.get(i).asString()));
        }

        TrieTreeSet tree = new TrieTreeSet();
        list = cfg.getOrCreateList("adblock");
        for (int i = 0; i < list.size(); i++) {
            CMapping map = list.get(i).asMap();
            File file = new File(map.getString("file"));
            if (System.currentTimeMillis() - file.lastModified() > map.getLong("update")) {
                String url = map.getString("url");
                if (!url.isEmpty()) {
                    System.out.println("Update " + file + " via " + url);
                    HttpConnection hc = new HttpConnection(new URL(url));
                    hc.connect();
                    InputStream in = hc.getInputStream();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        int read;
                        do {
                            byte[] buf = new byte[4096];
                            read = in.read(buf);
                            out.write(buf, 0, read);
                        } while (read > 0);
                    } finally {
                        hc.disconnect();
                    }
                }
            }

            CharList tmp = new CharList();
            try (SimpleLineReader scan = new SimpleLineReader(new FileInputStream(file))) {
                for (String ln : scan) {
                    if(ln.isEmpty() || ln.startsWith("!"))
                        continue;

                    tmp.clear();
                    tmp.append(ln)
                       .replace("@@", "")
                       .replace("|", "")
                       .replace("^", "");
                    tree.add(tmp.toString());
                }
            }
        }
        if (!tree.isEmpty()) {
            dns.blocked = s -> {
                for (int i = 0; i < s.length(); i++) {
                    if (tree.contains(s, i, s.length())) {
                        if (i != 0) System.out.println("Matched partial at " + i + " of " + s);
                        return true;
                    }
                }
                return false;
            };
        }
        dns.launch();

        /**
         * Run HTTP Server
         */
        int httpPort = cfg.getInteger("managePort");
        if(httpPort > 0) {
            InetSocketAddress ha = new InetSocketAddress(InetAddress.getLoopbackAddress(), httpPort);
            new HttpServer(ha, 256, dns).start();
            System.out.println("Http listening on " + ha);
        }

        if (cfg.containsKey("TTLFactor"))
            Record.ttlUpdateMultiplier = (float) cfg.getDouble("TTLFactor");
        /**
         * Use main thread as DNS Server
         */
        System.out.println("Welcome, to a cleaner world, " + System.getProperty("user.name", "user") + " !\n");
        try {
            roj.misc.CpFilter.registerShutdownHook();
        } catch (Error ignored) {}

        Thread.currentThread().setName("Dns Server");
        dns.run();
    }
}
