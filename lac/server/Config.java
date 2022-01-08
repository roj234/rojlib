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

import roj.config.JSONConfiguration;
import roj.config.data.CMapping;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * LAC Configuration
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/8 18:53
 */
public final class Config extends JSONConfiguration {
    public static boolean enableLoginModule;
    public static int loginTimeout, kickWrong;

    public static int noPass1Type, noPass2Type, noPass3Type,
            randomCheckInvMin, randomCheckInvDelta, randomCheckTimeout,
            classCheckTimeout, commandCheckTimeout;

    public static byte[] ENCRYPT_IV;

    static {
        new Config();
    }

    private Config() {
        super(new File("LAC.json"));
    }

    @Override
    protected void readConfig(CMapping map) {
        map.dot(true);

        File f = new File(map.putIfAbsent("服务端描述文件", "lac-server.mif"));
        if(!f.isFile())
            throw new RuntimeException("未找到描述文件, 请先配置LAC");
        try {
            ModInfo.init(f);
        } catch (IOException e) {
            throw new RuntimeException("描述文件 " + f.getAbsolutePath() + " 无法读取", e);
        }

        enableLoginModule = map.putIfAbsent("内置登录系统.启用", true);
        kickWrong = map.putIfAbsent("内置登录系统.几次后踢出密码输入错误的用户", 3);
        loginTimeout = map.putIfAbsent("内置登录系统.踢出没有登录成功的用户(tick)", 600);

        String sw = map.putIfAbsent("反作弊.未通过快速检查(反作弊错误,模组错误,挂起连接,中断连接)", "模组错误");
        noPass1Type = switchIt(sw);

        classCheckTimeout = map.putIfAbsent("反作弊.正式检查.超时(tick)", 200);
        sw = map.putIfAbsent("反作弊.正式检查.未通过(反作弊错误,模组错误,挂起连接,中断连接,随机异常,崩端)", "随机异常");
        noPass2Type = switchIt(sw);

        randomCheckTimeout  = map.putIfAbsent("反作弊.随机抽查.超时(tick)", 600);
        randomCheckInvMin   = map.putIfAbsent("反作弊.随机抽查.最短间隔(0关闭,tick)", 100);
        randomCheckInvDelta = map.putIfAbsent("反作弊.随机抽查.最长间隔", 1200) - randomCheckInvMin;
        sw = map.putIfAbsent("反作弊.随机抽查.未通过(反作弊错误,模组错误,挂起连接,中断连接,随机异常,崩端)", "随机异常");
        noPass3Type = switchIt(sw);

        commandCheckTimeout = map.putIfAbsent("反作弊.主动检查/截图.超时(tick)", 400);
        ENCRYPT_IV = map.putIfAbsent("反作弊.服务端验证密钥(可选)", "").getBytes(StandardCharsets.UTF_8);
    }

    private static int switchIt(String sw) {
        switch (sw) {
            default:
            case "反作弊错误":
                return 0;
            case "模组错误":
                return 1;
            case "挂起连接":
                return 2;
            case "中断连接":
                return 3;
            case "随机异常":
                return 4;
            case "崩端":
                return 5;
        }
    }
}
