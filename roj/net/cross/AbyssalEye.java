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
package roj.net.cross;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.NetworkUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/17 22:10
 */
public class AbyssalEye {
    public static void main(String[] args) throws IOException, ParseException, GeneralSecurityException {
        CMapping cfg;
        File config = new File(args.length > 0 ? args[0] : "config.json");
        if(!config.isFile()) {
            System.out.println("AbyssalEye <config>");
            return;
        }
        cfg = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(config))).asMap();
        cfg.dotMode(true);

        String controlIp = cfg.getString("host");
        InetAddress addr = controlIp.isEmpty() ? null : InetAddress.getByAddress(NetworkUtil.ip2bytes(controlIp));

        InetSocketAddress address = new InetSocketAddress(addr, cfg.getInteger("port"));
        if(cfg.getString("mode").equals("client")) {
            AEClient client = new AEClient(new InetSocketAddress(InetAddress.getLoopbackAddress(), cfg.getInteger("local_port")), cfg.getBool("ssl"));
            client.waitForLocal();
            client.connectAndWork(address);
        } else {
            AEServer server = cfg.getBool("ssl") ? new AEServer(address, cfg.getInteger("max_users"), cfg.getString("cert_path"), cfg.getString("cert_pass").toCharArray()) : new AEServer(address, cfg.getInteger("max_users"));

            Thread serverRunner = new Thread(server);
            serverRunner.setName("Server Thread");
            serverRunner.setDaemon(true);
            serverRunner.start();

            Thread main = Thread.currentThread();
            main.setName("Terminal");
            // todo
            try {
                Thread.sleep(999999999);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //server.shutdown();
        }

    }
}
