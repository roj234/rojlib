
#include <windows.h>
#include <shobjidl.h>

#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "shell32.lib")

static const IID MYIID_ITaskbarList3 = {0xea1afb91, 0x9e28, 0x4b86, {0x90, 0xe9, 0x9e, 0x9f, 0x8a, 0x5e, 0xef, 0xaf}};

// 初始化 COM 并创建 ITaskbarList3 接口
ITaskbarList3* InitializeTaskbarProgress() {
    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    if (FAILED(hr)) return nullptr;

    ITaskbarList3* pTaskbar = nullptr;
    hr = CoCreateInstance(
            CLSID_TaskbarList, NULL, CLSCTX_ALL,
            MYIID_ITaskbarList3, reinterpret_cast<void**>(&pTaskbar)
    );

    if (FAILED(hr)) {
        CoUninitialize();
        return nullptr;
    }

    if (FAILED(pTaskbar->HrInit())) {
        pTaskbar->Release();
        CoUninitialize();
        return nullptr;
    }

    return pTaskbar;
}

// 设置任务栏进度条状态和值
void SetTaskbarProgress(ITaskbarList3* pTaskbar, HWND hWnd, ULONGLONG progress, ULONGLONG total) {
    if (!pTaskbar) return;
    pTaskbar->SetProgressValue(hWnd, progress, total);
}
void SetTaskbarState(ITaskbarList3* pTaskbar, HWND hWnd, TBPFLAG flag) {
    if (!pTaskbar) return;
    pTaskbar->SetProgressState(hWnd, flag);
}

// 释放资源
void ReleaseTaskbarProgress(ITaskbarList3* pTaskbar) {
    if (pTaskbar) {
        pTaskbar->Release();
        CoUninitialize();
    }
}

/*int main() {
    // 初始化任务栏接口
    ITaskbarList3* pTaskbar = InitializeTaskbarProgress();
    HWND hWnd = GetConsoleWindow();  // 示例使用控制台窗口句柄（实际应用中替换为你的窗口句柄）

    SetTaskbarState(pTaskbar, hWnd, TBPF_NORMAL);  // 设置为正常模式

    // 模拟进度更新（例如在循环中）
    for (int i = 0; i <= 100; i++) {
        SetTaskbarProgress(pTaskbar, hWnd, i, 100);
        Sleep(50);  // 模拟耗时操作
    }

    SetTaskbarState(pTaskbar, hWnd, TBPF_NOPROGRESS);  // 设置为正常模式

    // 清理资源
    ReleaseTaskbarProgress(pTaskbar);
    return 0;
}*/