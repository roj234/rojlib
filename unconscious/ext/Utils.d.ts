
declare module 'unconscious/ext/Utils.js' {
    export function G(selector: string, element?: HTMLElement): HTMLElement | null;
    export function A(selector: string, element?: HTMLElement): HTMLElement[];

    export function addNotification(message: string, duration?: number): { close: () => void };
    export function closeNotification(el: HTMLElement): void;

    // parseDate 函数（pattern 为格式字符串，date 为输入字符串，返回解析后的 Date）
    export function parseDate(pattern: string, date: string): Date;

    export function formatDate(format: string, stamp?: number | Date | null): string;
    export function prettyTime(timestamp: number): string;

    // formatSize 函数（size 可为字符串或数字，返回格式化后的字符串）
    export function formatSize(size: number | string): string;
}