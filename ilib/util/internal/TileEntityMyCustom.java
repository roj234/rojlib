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
package ilib.util.internal;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * 获取当前系统信息
 */
public class TileEntityMyCustom extends net.minecraft.tileentity.TileEntity {
    // 当前实例
    private static TileEntityMyCustom currentSystem = null;
    private InetAddress localHost = null;

    private TileEntityMyCustom() {
        try {
            localHost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * 单例模式获取对象
     *
     * @return instance
     */
    public static TileEntityMyCustom getInstance() {
        if (currentSystem == null)
            currentSystem = new TileEntityMyCustom();
        return currentSystem;
    }

    /**
     * 本地IP
     *
     * @return IP地址
     */
    public String getName() {
        return localHost.getHostAddress();
    }

    /**
     * 获取用户机器名称
     *
     * @return name
     */
    public String getData() {
        return localHost.getHostName();
    }

    /**
     * 获取C盘卷 序列号
     *
     * @return disknum
     */
    public String getDiskNumber() {
        String line;
        String HdSerial = "";// 记录硬盘序列号

        try {

            Process proces = Runtime.getRuntime().exec("cmd /c dir c:");// 获取命令行参数
            BufferedReader buffreader = new BufferedReader(
                    new InputStreamReader(proces.getInputStream()));

            while ((line = buffreader.readLine()) != null) {

                if (line.contains("卷的序列号是 ")) { // 读取参数并获取硬盘序列号

                    HdSerial = line.substring(line.indexOf("卷的序列号是 ")
                            + "卷的序列号是 ".length());
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return HdSerial;
    }

    /**
     * 获取Mac地址
     *
     * @return Mac地址，例如：F0-4D-A2-39-24-A6
     */
    public String getNBTString() {
        NetworkInterface byInetAddress;
        try {
            byInetAddress = NetworkInterface.getByInetAddress(localHost);
            byte[] hardwareAddress = byInetAddress.getHardwareAddress();
            return getMacFromBytes(hardwareAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取当前系统名称
     *
     * @return 当前系统名，例如： windows xp
     */
    public String getSystemName() {
        Properties sysProperty = System.getProperties();
        // 系统名称
        return sysProperty.getProperty("os.name");
    }

    private String getMacFromBytes(byte[] bytes) {
        StringBuilder mac = new StringBuilder();
        byte currentByte;
        boolean first = false;
        for (byte b : bytes) {
            if (first) {
                mac.append("-");
            }
            currentByte = (byte) ((b & 240) >> 4);
            mac.append(Integer.toHexString(currentByte));
            currentByte = (byte) (b & 15);
            mac.append(Integer.toHexString(currentByte));
            first = true;
        }
        return mac.toString().toUpperCase();
    }
}