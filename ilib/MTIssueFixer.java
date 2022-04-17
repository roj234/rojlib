package ilib;

import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2022/4/19 18:12
 */
class MTIssueFixer extends Thread {
    public int trigger;

    public MTIssueFixer() {
        super("MTIssueFixer");
        setDaemon(true);
    }

    @Override
    public void run() {
        while (true) {
            while (trigger == 0) {
                LockSupport.park();
            }

            Thread server = ImpLib.proxy.serverThread;
            switch (trigger) {
                case 1: // 服务器停止
                    if (server != null) {
                        try {
                            server.interrupt();
                            server.join(10000);
                            System.out.println("等待服务器停止");
                        } catch (InterruptedException ignored) {}

                        if (server.isAlive()) {
                            try {
                                server.stop();
                            } catch (Throwable e) {
                                ImpLib.logger().error("无法终止进程", e);
                            }
                        }

                        trigger = 0;
                    }
                break;
                case 2: { // 调整优先级
                    Thread client = ((ClientProxy) ImpLib.proxy).clientThread;
                    int sop = server.getPriority();
                    int cop = client.getPriority();

                    server.setPriority(Thread.MAX_PRIORITY);
                    client.setPriority(Thread.MIN_PRIORITY);

                    System.out.println("更新优先级");

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}

                    server.setPriority(sop);
                    client.setPriority(cop);

                    trigger = 0;
                }
                break;
            }
        }
    }
}
