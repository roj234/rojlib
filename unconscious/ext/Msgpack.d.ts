
declare module 'unconscious/ext/Msgpack.js' {
    /**
     * 递归的 MsgPack Schema 类型定义
     */
    type MsgpackSchema = (string | MsgpackSchema)[];

    /**
     * MsgPack 解码选项
     */
    interface MsgpackDecodeOptions {
        /**
         * 使用 BigInt 而非 double，默认 false
         */
        bigint?: boolean;
        /**
         * Schema 定义，用于省略数据布局，默认 null
         */
        schema?: MsgpackSchema | null;
        /**
         * 扩展类型解码函数
         */
        decodeExt?: (dataView: DataView, type: number, offset: number, length: number) => any;
        /**
         * 是否一次解码多个对象，默认 false
         */
        multiple?: boolean;
    }

    /**
     * 支持的输入缓冲区类型
     * - Array: 假设为 number[] (如 Uint8Array 的数组表示)
     * - ArrayBuffer
     * - TypedArray: 如 Uint8Array 等 ArrayBufferView
     * - Buffer: Node.js Buffer
     * - DataView
     */
    // 注意：Buffer 是 Node.js 类型，需要在 tsconfig.json 中启用 "types": ["node"] 或类似
    type InputBuffer = number[] | ArrayBuffer | ArrayBufferView | Buffer | DataView;

    /**
     * MsgPack 解码主函数
     * @param input - 输入缓冲区
     * @param options - 解码选项，默认 null
     * @returns 解码结果（单个对象或对象数组，取决于 options.multiple）
     */
    export function decodeMsg(input: InputBuffer, options?: MsgpackDecodeOptions | null): any | any[];

    /**
     * MsgPack 底层解码函数（内部使用）
     * @param dataView - 数据视图缓冲区
     * @param offset - 起始偏移量
     * @param options - 解码选项
     * @returns [解码结果, 新偏移量]
     */
    export function decodeRawMsg(
        dataView: DataView,
        offset: number,
        options?: MsgpackDecodeOptions | null
    ): [any, number];

    /**
     * Msgpack 编码主函数
     * @returns 编码结果（该值可能在多次调用之间复用）
     */
    export function encodeMsg(data: any): Uint8Array;
}
