package roj.net.mss;

/**
 * @author solo6975
 * @since 2022/2/13 13:10
 */
@FunctionalInterface
public interface MSSEngineFactory {
    MSSEngine newEngine();
}
