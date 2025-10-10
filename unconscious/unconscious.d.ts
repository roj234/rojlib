// unconscious.d.ts
export declare namespace JSX {
    interface Element extends HTMLElement {}
}

/**
 * persist state的前缀，通常为项目名称
 * @default 'default'
 */
declare const UC_PERSIST_STORE: string;
/**
 * persist state修改时是否立即保存，如果关闭会在下列任一条件满足时保存:
 * a. 标签页关闭时beforeunload
 * b. 标签页失去焦点时visibilitychange
 * c. 一分钟没有新修改后
 * @default false
 */
declare const UC_IMMEDIATE_STORE: boolean;

/**
 * 是否允许响应式变量的值为多个DOM根节点（Fragment/Array） —— 警告，可能影响性能！
 * @default false
 */
declare const UC_REACTIVE_FRAGMENT: boolean;

declare module 'unconscious@micro' {
    export function createElement(type: string, props?: object, ...children: JSX.Element[]): Element;
    export function createFragment(...children: JSX.Element[] | JSX.Element[][]): JSX.Element[];
    export function appendChildren(parent: Element, children: JSX.Element[]): void;
}

declare module 'unconscious@shared' {
    export const PASSIVE_EVENT: {passive: true};
    export const ONCE_EVENT: {once: true};

    /**
     * 开发时获取带名称的符号
     * @param name 符号名称
     * @returns {symbol}
     */
    export function debugSymbol(name: string): symbol;

    /**
     * 存储简单类型时可使用AS_IS序列化器
     * @template T
     * @type {function(T): T}
     */
    export const AS_IS: Function;
}

declare module 'unconscious' {
    export type Renderable = string | JSX.Element | Readonly<Reactive<string | JSX.Element>>;
    export type Fragment = Array<Renderable>;

    export type Component = (props: Record<string, any>, children: Renderable[]) => Renderable;
    export type FC<P extends Record<string, any>, I extends Renderable> = ((props: P, children?: Renderable[]) => I) & Component;
    export type ImportedComponent = {default: Component};

    export type Reactive<T> = T & {
        value: T;
    }

    /**
     * 创建 DOM 元素
     */
    export function createElement(type: string, props?: object, ...children: Renderable[]): Element;
    /**
     * 创建文档片段
     */
    export function createFragment(...children: Renderable[]): Fragment;
    /**
     * 往父元素中插入子元素
     */
    export function appendChild(parent: Element, child: Renderable): void;
    /**
     * 往父元素中插入子元素
     */
    export function appendChildren(parent: Element, children: Fragment): void;

    export function isReactive(object: any): object is Reactive<any>;
    export function unconscious<T>(object: T | Reactive<T>): T;
    export function isPureObject(object: any): boolean;

    /**
     * 基于模板克隆静态的元素
     * @param node
     */
    export function kloneNode(node: string): () => JSX.Element;

    /**
     * 注册一个监听器以在元素被移除时移除
     * @param element - 元素
     * @param listenerKey - 监听器键
     */
    export function $disposable(element: Element | Text, listenerKey: [Reactive<any>, Function] | Function): void;
    export function $dispose(element: Element | Text, keep?: [Reactive<any>, Function]): void;

    export function $state<T>(object: T, deep?: boolean): Reactive<T>;
    export function $watch(objects: Reactive<any> | Reactive<any>[], listener: () => Function | void, triggerNow?: boolean): void;
    export function $watchOn(object: Reactive<any>, listener: () => void, element: HTMLElement): void;
    export function $unwatch(object: Reactive<any>, listener: Function): void;

    export function $computed<T>(
        callback: (oldValue?: T) => T | undefined,
        lazy?: boolean,
        dependencies?: Reactive<any>[]
    ): Readonly<Reactive<T>>;

    export function $update(objects: Reactive<any> | Reactive<any>[]): void;

    export function createComponent(
        component: Component,
        props: Record<string, any>,
        ...children: Renderable[]
    ): Renderable;

    /**
     * 监听响应式变量变化
     * 用于函数组件内部
     *
     * @param objects - 要监听的响应式变量或数组
     * @param listener - 变化回调函数（可返回清理函数）
     * @param [triggerNow=true] - 是否立即触发回调
     * @see $watch
     */
    export function $watchWithCleanup(objects: Reactive<any> | Reactive<any>[], listener: () => Function | void, triggerNow?: boolean): void;

    export function preserveState<T>(t: T): T;
    export function assertReactive<T>(t: Reactive<T> | object): Reactive<T>;

    /**
     * 创建按需更新的列表，复用现有DOM元素
     * @template T
     * @param {Reactive<T[]>} list - 响应式列表
     * @param {(item: T, index: number) => Renderable} renderItem - 生成列表项元素的函数 (item, index) => Element
     * @param {(item: T, index: number) => any} [keyFunc] - 生成唯一标识的函数 (item, index) => any (默认使用item)
     * @param {Map<T, Node>} currentKeys
     * @returns {DocumentFragment} 包含动态列表的文档片段
     */
    export function $foreach<T, S extends Renderable>(
        list: Reactive<T[]>,
        renderItem: (item: T, index: number) => S,
        keyFunc?: (item: T, index: number) => any,
        currentKeys?: any//Map<T, S>
    ): DocumentFragment;

    export function $forElse<T>(
        list: Reactive<T[]>,
        renderItem: (item: T, index: number) => Renderable,
        emptyElement: Renderable
    ): Readonly<Reactive<JSX.Element>>;

    export function $forElseAsyncState<T>(
        state: ReactivePromise<T[]>,
        renderItem: (item: T, index: number) => Renderable,
        emptyElement: Renderable,
        loadingElement: Renderable,
        errorElement?: (error: boolean | any) => Renderable
    ): Readonly<Reactive<JSX.Element>>;

    interface AnimationOptions {
        duration: number;
        easing?: 'ease' | 'linear' | 'ease-in' | 'ease-out' | 'ease-in-out' | string;
        from: Record<string, string | number>;
        to: Record<string, string | number>;
        acceptCancel?: boolean;
    }

    /**
     * 执行CSS动画
     * @param ref - 要执行动画的元素
     * @param options - 动画配置
     * @returns Promise<动画完成>
     */
    export function $animate(ref: HTMLElement | Reactive<HTMLElement>, options: Partial<AnimationOptions>): Promise<boolean>;
    /**
     * 中止动画
     * @param element - 要中止动画的元素
     * @note $animate返回的Promise将以false作为信息兑现
     */
    export function cancelAnimation(element: HTMLElement): void;

    /**
     * 创建或获取响应式全局状态存储
     *
     * @param key - 唯一标识符 (相同key返回相同实例)
     * @param initializer - 初始值或获取初始值的函数
     * @param options - 配置选项
     *
     * @example 基础用法
     * const userStore = $store('user', { name: 'Guest' }, { persist: true });
     * userStore.name = 'Alice';
     *
     * @note 特性说明
     * - 跨标签页同步
     * - 自动持久化 (可选立即或延迟保存)
     */
    export function $store<T>(
        key: string,
        initializer: T | (() => T),
        options?: {
            /**
             * 自动深层代理
             * @default true
             */
            deep?: boolean;
            /**
             * 持久化到localStorage
             * @default false
             */
            persist?: boolean;
            /**
             * 数据序列化方法
             * 仅persist=true时有效
             * @default JSON.stringify
             */
            ser?: (obj: T) => string;
            /**
             * 数据反序列化方法
             * 仅persist=true时有效
             * @default JSON.parse
             */
            deser?: (str: string) => T;
        }
    ): Reactive<T>;

    export type ReactivePromise<T> = Reactive<T> & {
        loading: boolean;
        error: false | Error;
    }

    /**
     * 异步状态
     * @param fetcher - 异步数据获取函数，接收参数并返回 Promise
     * @param value - 静态或响应式参数
     *
     * @example
     * // 基本用法
     * const params = $state(1);
     * const state = $asyncState(async (page) => {
     *   const res = await fetch(`/api?page=${page}`);
     *   return res.json();
     * }, params);
     *
     * // 在组件中使用
     * <div>
     *   {state.loading ? '加载中...' : state.error || state.value}
     * </div>
     *
     * @note
     * 特性：
     * 1. 响应式更新
     * 2. 保留旧数据，避免界面闪烁
     * 3. 自动管理 loading 和 error
     */
    export function $asyncState<T, R>(
        fetcher: (arg: T) => Promise<R>,
        value?: T | Reactive<T>
    ): ReactivePromise<R>;

    /**
     * 异步组件
     * @param loader - 组件加载函数
     * @param [loading="加载中..."] - 加载时显示的组件
     * @param [error=String(error)] - 出错时显示的组件
     */
    export function $asyncComponent(
        loader: () => Promise<ImportedComponent | Component>,
        loading?: Renderable | (() => Renderable),
        error?: Renderable | ((error: Error) => Renderable)
    ): Component;
}
