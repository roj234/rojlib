
declare module 'unconscious/ext/Request.js' {
    /**
     * AJAX 配置对象
     */
    interface AjaxConfig {
        /**
         * 请求方法：get/post，默认 "get"
         */
        method?: "get" | "post";
        /**
         * 请求超时时间（毫秒），默认 30000
         */
        timeout?: number;
        /**
         * 响应数据类型：text/json，默认 "text"
         */
        dataType?: "text" | "json";
        /**
         * XMLHttpRequest 的 responseType，默认 "json"
         */
        responseType?: "text" | "json" | "arraybuffer" | "blob" | ""; // 基于常见 XMLHttpRequest 值扩展
        /**
         * 请求头对象，默认 {}
         */
        header?: Record<string, string>;
        /**
         * 请求地址（必需）
         */
        url: string;
        /**
         * 请求成功回调函数，接收响应数据和 statusText
         */
        success?: (data: any, statusText: string) => void;
        /**
         * 请求失败回调函数，接收 xhr 对象和错误信息
         */
        error?: (xhr: XMLHttpRequest, error?: string) => void;
    }

    /**
     * 默认 AJAX 配置（示例，非类型定义部分）
     */
    export const ajaxConfig: AjaxConfig & {
        prefix: string;
    };

    /**
     * 发送 AJAX 请求
     *
     * 重载签名 1: 使用 URL 字符串作为第一个参数
     * @param url - 请求 URL 地址
     * @param data - 请求数据，对象或字符串格式
     * @param onSuccess - 成功回调函数，接收响应数据和 statusText（可选，使用 config.success）
     * @param onError - 错误回调函数，接收 xhr 对象和错误信息（可选，使用 config.error）
     * @returns void
     */
    export function ajax(
        url: string,
        data?: object | string,
        onSuccess?: (data: any, statusText: string) => void,
        onError?: (xhr: XMLHttpRequest, error?: string) => void
    ): void;

    /**
     * 重载签名 2: 使用 AjaxConfig 对象作为第一个参数
     * @param config - AJAX 配置对象
     * @param data - 请求数据（如果 config.url 已指定，可选）
     * @param onSuccess - 成功回调（可选，覆盖 config.success）
     * @param onError - 错误回调（可选，覆盖 config.error）
     * @returns void
     */
    export function ajax(
        config: AjaxConfig,
        data?: object | string,
        onSuccess?: (data: any, statusText: string) => void,
        onError?: (xhr: XMLHttpRequest, error?: string) => void
    ): void;
}