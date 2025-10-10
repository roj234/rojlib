import {JSX} from "../../unconscious";

declare module 'Filter.jsx' {
    // Config 类型定义
    interface Config {
        id: string;
        name: string;
        type: string;
    }

    // Filter 组件的 props 类型定义
    interface FilterProps {
        config: Config[]; // 配置列表
        choices?: object[]; // 默认选项值，默认空数组 []
        onChange?: (value: string, data: any, choices: object[]) => void | string; // 回调函数
    }

    function Filter(props: FilterProps): JSX.Element;
    export = Filter;
}