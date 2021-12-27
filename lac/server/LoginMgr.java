/*
 * This file is a part of MI
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

package lac.server;

import ilib.ImpLib;
import lac.server.packets.PacketLogin;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.util.ByteList;
import roj.util.FixedString;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.util.text.TextComponentTranslation;

import java.io.*;

/**
 * Login Manager
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/12/6 16:16
 */
public final class LoginMgr {
    private static final MyHashMap<String, Account> accounts = new MyHashMap<>();
    public static final MyHashSet<Account> pending = new MyHashSet<>(10);

    public static void update() {
        pending.removeIf(Account::update);
    }

    public static boolean removeAccount(String name) {
        Account account = accounts.get(name);
        if (account == null) return false;
        pending.remove(account);
        if (account.conn != null)
            account.disconnect("lac.login.pass_reset");
        account.pass = null;
        return true;
    }

    public static Account get(EntityPlayerMP p) {
        return accounts.get(p.getName().toLowerCase());
    }

    public static void handleConnect(String name, NetworkManager conn, Runnable callback) {
        name = name.toLowerCase();

        Account account = accounts.computeIfAbsent(name, (k) -> new Account(k, null, 0));
        account.init(conn, callback);
        pending.add(account);

        PacketLogin.send(conn, account.pass != null ? "lac.login.input_pass" : "lac.login.register");
    }

    public static void handlePacket(EntityPlayerMP p, String pass) {
        Account account = accounts.get(p.getName().toLowerCase());
        if (account != null && pending.contains(account) && account.login(pass)) {
            pending.remove(account);
        }
    }

    private static final ByteList writer = new ByteList(128 + 2);

    private static final RandomAccessFile userData;

    private static final FixedString FORMAT = new FixedString(64);

    private static final int USERDATA_HEADER = 0xa92f84d1;

    static {
        try {
            File file = new File("user.bin");
            if(!file.exists()) {
                OutputStream stream = new FileOutputStream(file);
                stream.write(new byte[] {
                        (byte) 0xa9, 0x2f, (byte) 0x84, (byte) 0xd1, 0x00, 0x00
                });
            }
            userData = new RandomAccessFile(file, "rw");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void updateOrAppendEntry(Account account) throws IOException {
        if(account.index == 0) {
            userData.seek(4);
            userData.writeShort(accounts.size());
            userData.seek(account.index = (int)userData.length());
        } else {
            userData.seek(account.index);
        }
        FORMAT.write(writer, account.name);
        FORMAT.write(writer, account.pass);

        userData.write(writer.list, 0, writer.wIndex());
        writer.clear();
    }

    public static void save() {
        try {
            for (Account account : accounts.values()) {
                if (account.index == 0) {
                    updateOrAppendEntry(account);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void load() {
        accounts.clear();

        try {
            userData.seek(0);
            int header = userData.readInt();
            if (header != USERDATA_HEADER) return;

            userData.seek(4);
            byte[] all = new byte[(int) userData.length() - 4];
            userData.readFully(all);
            ByteList r = new ByteList(all);
            int length = r.readUnsignedShort();
            for (int i = 0; i < length; i++) {
                String name = FORMAT.read(r);
                String pass = FORMAT.read(r);
                accounts.put(name, new Account(name, pass, r.rIndex() + 4));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Account {
        public final String name;
        protected String pass;
        protected int index;

        private int tick, wrong;
        protected NetworkManager conn;
        protected Runnable cb;

        public Account(String name, String pass, int index) {
            this.name = name;
            this.pass = pass;
            this.index = index;
        }

        public boolean update() {
            if(conn == null || !conn.isChannelOpen()) {
                return true;
            }

            if (tick++ % 5 == 0)
                sendMsg("lac.login.input_pass");
            if (tick >= Config.loginTimeout) {
                disconnect("lac.login.timeout");
                this.conn = null;
                return true;
            }
            return false;
        }

        public void disconnect(String msg) {
            TextComponentTranslation t = new TextComponentTranslation(msg);
            conn.sendPacket(new SPacketDisconnect(t), v -> conn.closeChannel(t));
            conn.disableAutoRead();
            ImpLib.proxy.runAtMainThread(false, () -> {
                conn.handleDisconnection();
            });
            conn = null;
        }

        public void sendMsg(String msg) {
            conn.sendPacket(new SPacketChat(new TextComponentTranslation(msg)));
        }

        public boolean login(String pass) {
            if(this.pass == null) {
                this.pass = pass;
                this.tick = 0;
                sendMsg("lac.login.registered");
                this.conn = null;
                if(cb != null) {
                    cb.run();
                    cb = null;
                }
                return true;
            }

            if (this.pass.equals(pass)) {
                this.tick = 0;
                sendMsg("lac.login.pass_ok");
                this.conn = null;
                if(cb != null) {
                    cb.run();
                    cb = null;
                }
                return true;
            }

            if (wrong++ >= Config.kickWrong) {
                disconnect("lac.login.pass_wrong");
            } else
                conn.sendPacket(new PacketLogin("lac.login.pass_wrong"));
            return false;
        }

        public void init(NetworkManager p, Runnable cb) {
            this.conn = p;
            this.cb = cb;
            this.tick = 0;
            this.wrong = 0;
        }

        public boolean changePassword(String o, String n) {
            if(conn != null || !o.equals(pass))
                return false;
            pass = n;
            return true;
        }
    }
}
