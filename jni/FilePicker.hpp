#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
    #include <windows.h>
    #include <commdlg.h>
    #include <shlobj.h>
#else
    #include <gtk/gtk.h>
#endif

#define PICKER_OPEN_FILE 1
#define PICKER_SAVE_FILE 2
#define PICKER_SELECT_FOLDER 3

#ifdef _WIN32
typedef const wchar_t* nyastring;
#else
typedef const char* nyastring;
#endif

#pragma pack (1)
typedef struct {
    nyastring title;
    nyastring default_path;
    nyastring default_name;
    nyastring filter_name;    // 例如: "Text Files"
    nyastring filter_pattern; // 例如: "*.txt;*.log"
    void* windowHandle;
    uint8_t mode;
} PickerOptions;
#pragma pack ()

// --- Windows 实现 ---
#ifdef _WIN32

static int CALLBACK BrowseCallbackProc(HWND hwnd, UINT uMsg, LPARAM lParam, LPARAM lpData) {
    if (uMsg == BFFM_INITIALIZED) {
        // lpData 传入的是我们的默认路径字符串
        SendMessageW(hwnd, BFFM_SETSELECTIONW, TRUE, lpData);
    }
    return 0;
}

PIDLIST_ABSOLUTE PathToPidl(const wchar_t* path) {
    PIDLIST_ABSOLUTE pidl = NULL;
    HRESULT hr = SHParseDisplayName(path, NULL, &pidl, 0, NULL);
    if (SUCCEEDED(hr)) {
        return pidl;
    }
    return NULL;
}

static inline wchar_t* file_picker(PickerOptions* opt) {
    static wchar_t path[MAX_PATH];

    if (opt->mode == PICKER_SELECT_FOLDER) {
        BROWSEINFOW bi = {0};
        bi.hwndOwner = (HWND)opt->windowHandle;
        bi.lpszTitle = opt->title;
        bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_USENEWUI/* | BIF_VALIDATE*/;
        // 如果你需要限制“根目录”（用户不能向上跳转），请设置 pidlRoot
        // bi.pidlRoot = opt->default_path ? PathToPidl(opt->default_path) : NULL;
        if (opt->default_path) {
            bi.lpfn = BrowseCallbackProc;
            bi.lParam = (LPARAM)opt->default_path;
        }
        LPITEMIDLIST pidl = SHBrowseForFolderW(&bi);
        if (pidl != NULL) {
            SHGetPathFromIDListW(pidl, path);
            CoTaskMemFree(pidl);
            // 如果之前手动创建了 pidlRoot，也需要在此释放
            // if (bi.pidlRoot) CoTaskMemFree((void*)bi.pidlRoot);
            return path;
        }
    } else {
        OPENFILENAMEW ofn = {sizeof(ofn)};
        ofn.hwndOwner = (HWND)opt->windowHandle;
        ofn.lpstrFile = path;
        if (opt->default_name) wcscpy(path, opt->default_name);
        ofn.nMaxFile = sizeof(path);
        ofn.lpstrTitle = opt->title;
        ofn.lpstrInitialDir = opt->default_path;
        ofn.Flags = opt->mode == PICKER_OPEN_FILE ? OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST : OFN_OVERWRITEPROMPT;

        // 转换 Filter 格式: "Text\0*.txt\0All\0*.*\0"
        wchar_t filter[256] = {0};
        if (opt->filter_name && opt->filter_pattern) {
            my_wsnprintf(filter, sizeof(filter), L"%s (%s)\0%s\0All Files (*.*)\0*.*\0",
                     opt->filter_name, opt->filter_pattern, opt->filter_pattern);
            ofn.lpstrFilter = filter;
        } else {
            ofn.lpstrFilter = L"All Files (*.*)\0*.*\0";
        }

        BOOL result = (opt->mode == PICKER_OPEN_FILE) ? GetOpenFileNameW(&ofn) : GetSaveFileNameW(&ofn);
        if (result) return path;
    }
    return NULL;
}
#endif

// --- Linux 实现 (GTK3) ---
#ifndef _WIN32
static inline char* file_picker(PickerOptions* opt) {
    gtk_init(NULL, NULL);
    GtkFileChooserAction action;
    const char *button_text;

    if (opt->mode == PICKER_OPEN_FILE) {
        action = GTK_FILE_CHOOSER_ACTION_OPEN;
        button_text = "_Open";
    } else if (opt->mode == PICKER_SAVE_FILE) {
        action = GTK_FILE_CHOOSER_ACTION_SAVE;
        button_text = "_Save";
    } else {
        action = GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER;
        button_text = "_Select";
    }

    GtkWidget *dialog = gtk_file_chooser_dialog_new(opt->title, NULL, action,
                                                  "_Cancel", GTK_RESPONSE_CANCEL,
                                                  button_text, GTK_RESPONSE_ACCEPT, NULL);

    GtkFileChooser *chooser = GTK_FILE_CHOOSER(dialog);

    if (opt->default_path) gtk_file_chooser_set_current_folder(chooser, opt->default_path);
    if (opt->mode == PICKER_SAVE_FILE && opt->default_name) gtk_file_chooser_set_current_name(chooser, opt->default_name);

    if (opt->filter_pattern) {
        GtkFileFilter *filter = gtk_file_filter_new();
        gtk_file_filter_set_name(filter, opt->filter_name);
        // GTK patterns are like *->txt, split by space or add multiple
        gtk_file_filter_add_pattern(filter, opt->filter_pattern);
        gtk_file_chooser_add_filter(chooser, filter);
    }

    char *res_path = NULL;
    if (gtk_dialog_run(GTK_DIALOG(dialog)) == GTK_RESPONSE_ACCEPT) {
        res_path = gtk_file_chooser_get_filename(chooser);
    }

    // 注意：在实际生产中，res_path 需要由调用者释放或复制，此处为了演示直接打印
    static char output[1024];
    if (res_path) {
        strncpy(output, res_path, 1024);
        g_free(res_path);
        gtk_widget_destroy(dialog);
        while (gtk_events_pending()) gtk_main_iteration();
        return output;
    }

    gtk_widget_destroy(dialog);
    return NULL;
}
#endif


JNIEXPORT jstring JNICALL Java_roj_webui_WebUI_pickFile0(JNIEnv* env, jclass, jlong pOpt) {
#ifdef _WIN32
    const wchar_t* path = file_picker((PickerOptions*)pOpt);
    return !path ? NULL : env->NewString((jchar*)path, wcslen(path));
#endif
#ifndef _WIN32
    const char* path = file_picker((PickerOptions*)pOpt);
    return !path ? NULL : env->NewStringUTF(path, strlen(path));
#endif
}
