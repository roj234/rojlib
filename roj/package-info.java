/**
 * @since 2020/11/1 13:02
 */
package roj;

/**
 * 接口应该针对提供者来设计，而不是调用者。假如你纠结于到底这个接口应该从哪个模块的角度来考虑，那么可以肯定你需要使用桥接模式。
 *
 * <p>
 * for ( column = 0; column < 100; column++ ) {
 * for ( row = 0; row < 5; row++ ) {
 * sum = sum + table[ row ][ column ];
 * }
 * }
 * 这不好，循环次数小的应该在外面
 **/