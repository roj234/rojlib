/**
 * @since 2020/11/1 13:02
 */
package roj;

/**
 * 接口应该针对提供者来设计，而不是调用者。假如你纠结于到底这个接口应该从哪个模块的角度来考虑，那么可以肯定你需要使用桥接模式。
 *
 * Use Alt+Shift+C to quickly review recent changes to the project.
 * <p>
 * Java Example of a Nested Loop That Can Be Improved
 * <p>
 * for ( column = 0; column < 100; column++ ) {
 * for ( row = 0; row < 5; row++ ) {
 * sum = sum + table[ row ][ column ];
 * }
 * }
 * <p>
 * <p>
 * The key to improving the loop is that the outer loop executes much more often than the inner loop.
 * Each time the loop executes, it has to initialize the loop index, increment it on each pass through
 * the loop, and check it after each pass. The total number of loop executions is 100
 * for the outer loop and 100 * 5 = 500 for the inner loop, for a total of 600 iterations.
 * By merely switching the inner and outer loops, you can change the total number of iterations to 5
 * for the outer loop and 5 * 100 = 500 for the inner loop, for a total of 505 iterations.
 * Analytically, you'd expect to save about (600 - 505) / 600 = 16 percent by switching the loops.
 * <p>
 * <p>
 * <p>
 * for (int i = 0; i < 100; i++)
 * for (int j = 0; j < 20; j++)
 * a[j] = a[j] + 1;
 * =>
 * for (int j = 0; j < 20; j++)
 * for (int i = 0; i < 100; i++)
 * a[j] = a[j] + 1;
 * <p>
 * 减少内存的访问次数
 * <p>
 * 没必要的时候,不要乱做优化.首先,凭"一般常识"而不是profile做出的优化决定很可能并不会给程序带来显著的性能提升.
 * 其次,耍小聪明的优化反而可能干扰编译器的判断,从而阻挠了一些优化,反而使代码变得更慢.那就得不偿失了.
 **/

/**
 * 直接内存适合申请次数少，读写频繁的场合。
 * 考虑到优化因素后
 */

/**
 * 有锁对象， **只有一个**  线程获取锁，则采用偏向锁模式可以大大提高性能。
 * -XX:+UseBiasedLocking代表启用偏向锁
 * 激烈的竞争一般禁用偏向锁（-XX:-UseBiasedLocking）
 *
 * 偏向锁运行在一个线程进入 同步快的 时候，当第二个线程加入锁征用的时候，偏向锁就会升级为轻量级锁
 *
 * 自旋锁， 自适应锁
 *
 * 去除不可能存在共享资源竞争的锁，通过锁消除，节省无意义的请求锁的时间
 * -XX:+EliminateLocks
 *
 * volatile : 对此变量的写操作对任何线程可见
 */