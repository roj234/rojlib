
declare module 'unconscious/ext/VirtualList.js' {
    // 项目渲染出元素的索引，因为实际有它所以 ITEM_KEY 才可以重复
    export const INDEX: unique symbol;
    // 该项目的键，在数组中可以重复，如果和上次结果不同就重新渲染项目
    export const ITEM_KEY: unique symbol;
    // 该项目的高度
    export const ITEM_HEIGHT: unique symbol;

    /**
     * 虚拟列表配置选项
     */
    export interface VirtualListConfig<T> {
        /**
         * 父元素
         */
        element: HTMLElement;
        /**
         * 数据源
         */
        data: T[];
        /**
         * 每项的高度（不能为空，否则抛错）
         */
        itemHeight: number;
        /**
         * 渲染函数
         */
        renderer: (data: T, index: number, recycle: HTMLElement[]) => HTMLElement;
        /**
         * 生成唯一索引的函数
         */
        keyFunc?: (item: T) => any;
        /**
         * 总高度，默认使用 element.offsetHeight
         */
        height?: number;
        /**
         * 当前是否可见，默认根据 element 的 display 计算（auto）
         */
        visible?: boolean;
        /**
         * 元素高度是否固定（影响渲染模式）
         */
        fixed?: boolean;
    }

    /**
     * 虚拟列表类
     * @template T 数据类型（默认 Object）
     */
    export class VirtualList<T = object> {
        /**
         * 内部垫高容器
         */
        readonly dom: HTMLDivElement;
        /**
         * 每项的预期高度 (实际高度可以不同)
         */
        readonly itemHeight: number;
        /**
         * 外部容器高度
         */
        height: number;
        /**
         * 数据源
         */
        items: T[];
        /**
         * 渲染函数
         */
        readonly renderer: (data: T, index: number, recycle: HTMLElement[]) => HTMLElement;

        /**
         * 构造函数
         * @param config 配置选项
         */
        constructor(config: VirtualListConfig<T>);

        /**
         * 子元素或列表高度修改后重新计算需要渲染的项目
         * 传入 true 仅更新列表高度
         * @param itemHeightUnchanged 子元素高度未变化
         */
        repaint(itemHeightUnchanged?: boolean): void;

        /**
         * 更新数组某项并(立即)更新虚拟列表，如果这项正在渲染
         * @param i 数组索引
         * @param item 新的值（如果为 null/undefined，则使用当前 items[i]）
         * @param heightUnchanged 高度是否未变
         */
        setItem(i: number, item: T | null | undefined, heightUnchanged?: boolean): void;

        /**
         * 更新数组并(立即)更新虚拟列表
         * @param items 新的数据源
         */
        setItems(items: T[]): void;

        /**
         * 获取 DOM 中低 N 项（相对于当前 _start）
         * @param i 全局索引
         * @returns 当前渲染的元素（如果超出范围，返回 undefined）
         */
        getValue(i: number): HTMLElement | undefined;

        /**
         * 查找当前可见的项目索引
         * @param item 要查找的项目
         * @returns 索引（如果未找到，返回 -1）
         */
        findIndex(item: T): number;

        /**
         * 查找当前可见的项目索引（基于谓词）
         * @param predicate 匹配谓词
         * @returns 索引（如果未找到，返回 -1）
         */
        indexMatch(predicate: (item: T) => boolean): number;

        /**
         * 从回收池中查找并复用元素（已弃用）
         * @param match CSS 选择器模式
         * @returns 匹配的元素（如果未找到，返回 null）
         * @deprecated 都用框架了还关心这个？
         */
        findElement(match: string): HTMLElement | null;

        /**
         * 清理资源（断开观察器和事件监听）
         */
        destroy(): void;
    }
}
