package lac.server.note;

/**
 * dynamic全部混淆 <br> static 全部插入
 *
 * @author Roj233
 * @since 2021/7/10 13:53
 */
public @interface DefaultObfuscatePolicy {
    boolean onlyHaveStatic();
}
