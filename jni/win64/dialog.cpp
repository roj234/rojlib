
#include <windows.h>
#include <commdlg.h>  // 文件对话框相关头文件
#pragma comment(lib, "comdlg32.lib")  // 链接库

static const wchar_t filter[] =
        L"Text Files (*.txt)\0*.txt\0"
        L"All Files (*.*)\0*.*\0"
        L"\0";  // 结束符

static void OpenFileDialog(HWND hwnd) {
    OPENFILENAMEW ofn = {0};
    wchar_t fileName[MAX_PATH] = {0};

    // 初始化结构体
    ofn.lStructSize = sizeof(OPENFILENAMEW);
    ofn.hwndOwner = hwnd;  // 父窗口句柄
    ofn.lpstrFilter = filter;  // 文件筛选器
    ofn.lpstrFile = fileName;  // 存储返回的文件路径
    ofn.nMaxFile = MAX_PATH;
    ofn.lpstrTitle = L"选择你的文件";  // 自定义标题
    ofn.lpstrInitialDir = L"C:\\";  // 默认目录
    ofn.Flags = OFN_FILEMUSTEXIST | OFN_NOCHANGEDIR;  // 标志位

    // 调用打开对话框
    if (GetOpenFileNameW(&ofn)) {
        // 用户选择了文件，路径保存在 fileName 中
        MessageBoxW(hwnd, fileName, L"选择的文件", MB_OK);
    }
}

static void SaveFileDialog(HWND hwnd) {
    OPENFILENAMEW ofn = {0};
    wchar_t fileName[MAX_PATH] = L"默认文件名.txt";

    ofn.lStructSize = sizeof(OPENFILENAMEW);
    ofn.hwndOwner = hwnd;
    ofn.lpstrFilter = filter;
    ofn.lpstrFile = fileName;
    ofn.nMaxFile = MAX_PATH;
    ofn.lpstrTitle = L"保存文件";
    ofn.lpstrInitialDir = L"D:\\保存路径";
    ofn.Flags = OFN_OVERWRITEPROMPT | OFN_NOCHANGEDIR;

    if (GetSaveFileNameW(&ofn)) {
        // 处理保存逻辑

    }
}
