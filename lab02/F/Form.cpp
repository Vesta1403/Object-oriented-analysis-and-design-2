#include <windows.h>
#include <winrt/Windows.Foundation.h>
#include <strsafe.h>
#include "BluetoothFacade.h"   // новый класс-фасад

using namespace winrt;

#define IDC_BUTTON_TOGGLE   1001
#define IDC_STATIC_STATUS   1002
#define IDC_STATIC_CONNECTED 1003
#define IDC_LIST_CONNECTED   1004
#define IDC_STATIC_PAIRED    1005
#define IDC_LIST_PAIRED      1006
#define IDC_STATIC_AVAILABLE 1007
#define IDC_LIST_AVAILABLE   1008
#define IDC_BUTTON_REFRESH   1009
#define IDC_BUTTON_CONNECT   1010
#define IDC_BUTTON_DISCONNECT 1011

LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam);

// Функция обновления списков устройств
void RefreshLists(HWND hListConnected, HWND hListPaired, HWND hListAvailable, BluetoothFacade* bt)
{
    // Очистка списков
    SendMessage(hListConnected, LB_RESETCONTENT, 0, 0);
    SendMessage(hListPaired, LB_RESETCONTENT, 0, 0);
    SendMessage(hListAvailable, LB_RESETCONTENT, 0, 0);

    if (!bt || !bt->IsEnabled())
    {
        SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)L"Bluetooth выключен");
        return;
    }

    HCURSOR hOldCursor = SetCursor(LoadCursor(NULL, IDC_WAIT));
    ShowCursor(TRUE);

    auto devices = bt->ScanDevices();

    SetCursor(hOldCursor);
    ShowCursor(FALSE);

    if (!bt->LastError().empty())
    {
        wchar_t buf[256];
        StringCchPrintf(buf, 256, L"Ошибка поиска: %ls", bt->LastError().c_str());
        SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)buf);
        return;
    }

    for (const auto& dev : devices)
    {
        wchar_t entry[512];
        StringCchPrintf(entry, 512, L"%ls [%012I64X]", dev.name, dev.address);

        if (dev.connected)
        {
            int idx = SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)entry);
            SendMessage(hListConnected, LB_SETITEMDATA, idx, (LPARAM)dev.address);
        }
        else if (dev.authenticated)
        {
            int idx = SendMessage(hListPaired, LB_ADDSTRING, 0, (LPARAM)entry);
            SendMessage(hListPaired, LB_SETITEMDATA, idx, (LPARAM)dev.address);
        }
        else
        {
            int idx = SendMessage(hListAvailable, LB_ADDSTRING, 0, (LPARAM)entry);
            SendMessage(hListAvailable, LB_SETITEMDATA, idx, (LPARAM)dev.address);
        }
    }
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow)
{
    winrt::init_apartment();
    const wchar_t CLASS_NAME[] = L"МоеОкно";

    WNDCLASS wc = {};
    wc.lpfnWndProc = WindowProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = CLASS_NAME;
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);

    RegisterClass(&wc);

    HWND hwnd = CreateWindowEx(
        0, CLASS_NAME, L"Наушники", WS_OVERLAPPEDWINDOW,
        900, 400, 600, 400,
        NULL, NULL, hInstance, NULL
    );

    if (hwnd == NULL) return 0;

    ShowWindow(hwnd, nCmdShow);
    UpdateWindow(hwnd);

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return (int)msg.wParam;
}

LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
    static BluetoothFacade* bt = nullptr;
    static HWND hButtonToggle;        // Кнопка включения
    static HWND hStatus;              // "Bluetooth включен"
    static HWND hListConnected;       // Список подключённых устройств
    static HWND hListPaired;          // Список сопряжённых устройств
    static HWND hListAvailable;       // Список доступных устройств
    static HWND hButtonRefresh;       // Кнопка обновления
    static HWND hButtonConnect;       // Кнопка подключения
    static HFONT hLargeFont;          // Шрифт
    static HWND hButtonDisconnect;    // Кнопка отключения

    switch (uMsg)
    {
    case WM_CREATE:
    {
        HINSTANCE hInst = (HINSTANCE)GetWindowLongPtr(hwnd, GWLP_HINSTANCE);

        // Создание элементов управления (без изменений)
        hButtonToggle = CreateWindow(L"BUTTON", L"ВКЛ", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            20, 20, 100, 30, hwnd, (HMENU)IDC_BUTTON_TOGGLE, hInst, NULL);
        hStatus = CreateWindow(L"STATIC", L"Bluetooth включен", WS_CHILD | SS_LEFT,
            140, 20, 150, 30, hwnd, (HMENU)IDC_STATIC_STATUS, hInst, NULL);
        ShowWindow(hStatus, SW_HIDE);

        CreateWindow(L"STATIC", L"Подключенные:", WS_CHILD | SS_LEFT,
            20, 60, 150, 25, hwnd, (HMENU)IDC_STATIC_CONNECTED, hInst, NULL);
        hListConnected = CreateWindow(L"LISTBOX", NULL,
            WS_CHILD | WS_BORDER | LBS_NOTIFY | WS_VSCROLL,
            20, 85, 180, 150, hwnd, (HMENU)IDC_LIST_CONNECTED, hInst, NULL);

        CreateWindow(L"STATIC", L"Сопряженные:", WS_CHILD | SS_LEFT,
            210, 60, 150, 25, hwnd, (HMENU)IDC_STATIC_PAIRED, hInst, NULL);
        hListPaired = CreateWindow(L"LISTBOX", NULL,
            WS_CHILD | WS_BORDER | LBS_NOTIFY | WS_VSCROLL,
            210, 85, 180, 150, hwnd, (HMENU)IDC_LIST_PAIRED, hInst, NULL);

        CreateWindow(L"STATIC", L"Доступные:", WS_CHILD | SS_LEFT,
            400, 60, 150, 25, hwnd, (HMENU)IDC_STATIC_AVAILABLE, hInst, NULL);
        hListAvailable = CreateWindow(L"LISTBOX", NULL,
            WS_CHILD | WS_BORDER | LBS_NOTIFY | WS_VSCROLL,
            400, 85, 180, 150, hwnd, (HMENU)IDC_LIST_AVAILABLE, hInst, NULL);

        hButtonRefresh = CreateWindow(L"BUTTON", L"Обновить", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            20, 250, 100, 30, hwnd, (HMENU)IDC_BUTTON_REFRESH, hInst, NULL);
        hButtonConnect = CreateWindow(L"BUTTON", L"Подключить", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            130, 250, 100, 30, hwnd, (HMENU)IDC_BUTTON_CONNECT, hInst, NULL);
        hButtonDisconnect = CreateWindow(L"BUTTON", L"Отключить", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            240, 250, 100, 30, hwnd, (HMENU)IDC_BUTTON_DISCONNECT, hInst, NULL);

        // Шрифт
        hLargeFont = CreateFont(18, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
            DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            DEFAULT_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Tahoma");
        SendMessage(hListConnected, WM_SETFONT, (WPARAM)hLargeFont, TRUE);
        SendMessage(hListPaired, WM_SETFONT, (WPARAM)hLargeFont, TRUE);
        SendMessage(hListAvailable, WM_SETFONT, (WPARAM)hLargeFont, TRUE);
        SendMessage(hButtonToggle, WM_SETFONT, (WPARAM)hLargeFont, TRUE);
        SendMessage(hStatus, WM_SETFONT, (WPARAM)hLargeFont, TRUE);
        SendMessage(hButtonRefresh, WM_SETFONT, (WPARAM)hLargeFont, TRUE);
        SendMessage(hButtonConnect, WM_SETFONT, (WPARAM)hLargeFont, TRUE);

        // Создаём фасад
        bt = new BluetoothFacade();
        if (!bt->IsEnabled())
        {
            MessageBox(hwnd,
                L"Не удалось инициализировать Bluetooth.\n"
                L"Убедитесь, что адаптер существует и разрешение дано.",
                L"Ошибка", MB_OK | MB_ICONERROR);
        }
        else
        {
            bool isOn = bt->IsEnabled();
            SetWindowText(hButtonToggle, isOn ? L"ВЫКЛ" : L"ВКЛ");
            ShowWindow(hStatus, isOn ? SW_SHOW : SW_HIDE);
            ShowWindow(hListConnected, isOn ? SW_SHOW : SW_HIDE);
            ShowWindow(hListPaired, isOn ? SW_SHOW : SW_HIDE);
            ShowWindow(hListAvailable, isOn ? SW_SHOW : SW_HIDE);
            ShowWindow(hButtonRefresh, isOn ? SW_SHOW : SW_HIDE);
            ShowWindow(hButtonConnect, isOn ? SW_SHOW : SW_HIDE);
            ShowWindow(hButtonDisconnect, isOn ? SW_SHOW : SW_HIDE);
            if (isOn)
                RefreshLists(hListConnected, hListPaired, hListAvailable, bt);
            else
                SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)L"Bluetooth выключен");
        }
        return 0;
    }

    case WM_COMMAND:
    {
        if (LOWORD(wParam) == IDC_BUTTON_TOGGLE && HIWORD(wParam) == BN_CLICKED)
        {
            if (!bt) return 0;
            bool isCurrentlyOn = bt->IsEnabled();
            bool success = bt->SetEnabled(!isCurrentlyOn);
            if (success)
            {
                bool nowOn = !isCurrentlyOn;
                SetWindowText(hButtonToggle, nowOn ? L"ВЫКЛ" : L"ВКЛ");
                ShowWindow(hStatus, nowOn ? SW_SHOW : SW_HIDE);
                ShowWindow(hListConnected, nowOn ? SW_SHOW : SW_HIDE);
                ShowWindow(hListPaired, nowOn ? SW_SHOW : SW_HIDE);
                ShowWindow(hListAvailable, nowOn ? SW_SHOW : SW_HIDE);
                ShowWindow(hButtonRefresh, nowOn ? SW_SHOW : SW_HIDE);
                ShowWindow(hButtonConnect, nowOn ? SW_SHOW : SW_HIDE);
                ShowWindow(hButtonDisconnect, nowOn ? SW_SHOW : SW_HIDE);
                if (nowOn)
                    RefreshLists(hListConnected, hListPaired, hListAvailable, bt);
                else
                    SendMessage(hListConnected, LB_RESETCONTENT, 0, 0);
            }
            else
            {
                MessageBox(hwnd, bt->LastError().c_str(), L"Ошибка", MB_OK | MB_ICONERROR);
            }
            return 0;
        }
        else if (LOWORD(wParam) == IDC_BUTTON_REFRESH && HIWORD(wParam) == BN_CLICKED)
        {
            if (!bt) return 0;
            if (bt->IsEnabled())
                RefreshLists(hListConnected, hListPaired, hListAvailable, bt);
            else
                MessageBox(hwnd, L"Bluetooth выключен. Включите его перед обновлением.", L"Информация", MB_OK);
            return 0;
        }
        else if (LOWORD(wParam) == IDC_BUTTON_CONNECT && HIWORD(wParam) == BN_CLICKED)
        {
            if (!bt) return 0;
            HWND hList = nullptr;
            if (GetFocus() == hListConnected) hList = hListConnected;
            else if (GetFocus() == hListPaired) hList = hListPaired;
            else if (GetFocus() == hListAvailable) hList = hListAvailable;

            if (!hList)
            {
                if (SendMessage(hListConnected, LB_GETCURSEL, 0, 0) != LB_ERR) hList = hListConnected;
                else if (SendMessage(hListPaired, LB_GETCURSEL, 0, 0) != LB_ERR) hList = hListPaired;
                else if (SendMessage(hListAvailable, LB_GETCURSEL, 0, 0) != LB_ERR) hList = hListAvailable;
            }

            if (hList)
            {
                if (bt->ConnectSelected(hList, hwnd))
                {
                    MessageBox(hwnd, L"Подключение инициировано", L"Информация", MB_OK);
                    RefreshLists(hListConnected, hListPaired, hListAvailable, bt);
                }
                else
                {
                    MessageBox(hwnd, bt->LastError().c_str(), L"Ошибка", MB_OK | MB_ICONERROR);
                }
            }
            else
            {
                MessageBox(hwnd, L"Выберите устройство из списка", L"Предупреждение", MB_OK);
            }
            return 0;
        }
        else if (LOWORD(wParam) == IDC_BUTTON_DISCONNECT && HIWORD(wParam) == BN_CLICKED)
        {
            if (!bt) return 0;
            HWND hList = nullptr;
            if (GetFocus() == hListConnected) hList = hListConnected;
            else if (GetFocus() == hListPaired) hList = hListPaired;
            else if (GetFocus() == hListAvailable) hList = hListAvailable;

            if (!hList)
            {
                if (SendMessage(hListConnected, LB_GETCURSEL, 0, 0) != LB_ERR) hList = hListConnected;
                else if (SendMessage(hListPaired, LB_GETCURSEL, 0, 0) != LB_ERR) hList = hListPaired;
                else if (SendMessage(hListAvailable, LB_GETCURSEL, 0, 0) != LB_ERR) hList = hListAvailable;
            }

            if (hList)
            {
                if (bt->DisconnectSelected(hList))
                {
                    MessageBox(hwnd, L"Отключение инициировано", L"Информация", MB_OK);
                    RefreshLists(hListConnected, hListPaired, hListAvailable, bt);
                }
                else
                {
                    MessageBox(hwnd, bt->LastError().c_str(), L"Ошибка", MB_OK | MB_ICONERROR);
                }
            }
            else
            {
                MessageBox(hwnd, L"Выберите устройство из списка", L"Предупреждение", MB_OK);
            }
            return 0;
        }
        return 0;
    }

    case WM_DESTROY:
        if (hLargeFont) DeleteObject(hLargeFont);
        delete bt;
        bt = nullptr;
        PostQuitMessage(0);
        return 0;
    }

    return DefWindowProc(hwnd, uMsg, wParam, lParam);
}