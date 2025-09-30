// unconscious.d.ts

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
    export type ImportedComponent = {default: Component};

    export const UC_PERSIST_STORE: string;
    export const UC_IMMEDIATE_STORE: boolean;
    export const UC_REACTIVE_FRAGMENT: boolean;

    export interface Reactive<T> {
        value: T;
    }

    export function createElement(type: string, props?: object, ...children: Renderable[]): Element;
    export function createFragment(...children: Renderable[]): Fragment;
    export function appendChildren(parent: Element, children: Fragment): void;

    export function isReactive(object: any): object is Reactive<any>;
    export function unconscious<T>(object: T | Reactive<T>): T;
    export function isPureObject(object: any): boolean;

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

    export function preserveState<T>(t: T): T;
    export function $watchWithCleanup(objects: Reactive<any> | Reactive<any>[], listener: () => Function | void, triggerNow?: boolean): void;

    export interface PropertyCheckDecl {
        type?: any;
        required?: boolean;
    }

    export function checkProps(props: any[], declaration: Record<string, PropertyCheckDecl>): void;
    export function assertReactive<T>(t: Reactive<T> | object): Reactive<T>;

    export function $foreach<T>(
        list: Reactive<T[]>,
        renderItem: (item: T, index: number) => Renderable,
        keyFunc?: (item: T, index: number) => any
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

    export interface AnimationOptions {
        duration: number;
        easing?: 'ease' | 'linear' | 'ease-in' | 'ease-out' | 'ease-in-out' | string;
        from: Record<string, string | number>;
        to: Record<string, string | number>;
        acceptCancel?: boolean;
    }

    export function $animate(ref: HTMLElement | Reactive<HTMLElement>, options: Partial<AnimationOptions>): Promise<boolean>;
    export function cancelAnimation(element: HTMLElement): void;

    interface ObjectStore<T> extends Reactive<T> {
        $serialize: (value: T) => string;
        $deserialize: (serialized: string) => T;
        $snapshot: () => T;
        $dispose: () => void;
    }

    export function $store<T>(
        key: string,
        initializer: T | (() => T),
        options?: {
            serialize?: (value: T) => string;
            deserialize?: (serialized: string) => T;
            namespace?: string;
            persist?: boolean;
            deep?: boolean;
        }
    ): ObjectStore<T>;

    export interface ReactivePromise<T> extends Reactive<T | null> {
        loading: boolean;
        error: boolean | Error;
    }

    export function $asyncState<T, R>(
        fetcher: (arg: T) => Promise<R>,
        value?: T | Reactive<T>
    ): ReactivePromise<R>;

    export function $asyncComponent(
        loader: () => Promise<ImportedComponent | Component>,
        options?: {
            loading?: () => Renderable;
            error?: (error: any) => Renderable;
        }
    ): Component;
}
