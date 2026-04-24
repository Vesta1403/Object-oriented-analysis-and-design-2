#include "DeviceListManager.h"
#include "Bl.h"                // IsBluetoothEnabled
#include "list.h"              // EnumerateBluetoothDevices, BluetoothDeviceInfo
#include <strsafe.h>           // для StringCchPrintf

void RefreshDeviceLists(HWND hListConnected, HWND hListPaired, HWND hListAvailable)
{
    // Очистка списков
    SendMessage(hListConnected, LB_RESETCONTENT, 0, 0);
    SendMessage(hListPaired, LB_RESETCONTENT, 0, 0);
    SendMessage(hListAvailable, LB_RESETCONTENT, 0, 0);

    if (!IsBluetoothEnabled())
    {
        SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)L"Bluetooth выключен");
        return;
    }

    HCURSOR hOldCursor = SetCursor(LoadCursor(NULL, IDC_WAIT));
    ShowCursor(TRUE);

    BluetoothDeviceInfo* devices = NULL;
    int count = 0;
    int result = EnumerateBluetoothDevices(&devices, &count);

    SetCursor(hOldCursor);
    ShowCursor(FALSE);

    if (result != 0)
    {
        wchar_t buf[256];
        StringCchPrintf(buf, 256, L"Ошибка поиска: %d", result);
        SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)buf);
        free(devices);
        return;
    }

    for (int i = 0; i < count; i++)
    {
        wchar_t entry[512];
        StringCchPrintf(entry, 512, L"%ls [%012I64X]", devices[i].name, devices[i].address);

        if (devices[i].connected)
        {
            int idx = SendMessage(hListConnected, LB_ADDSTRING, 0, (LPARAM)entry);
            SendMessage(hListConnected, LB_SETITEMDATA, idx, (LPARAM)devices[i].address);
        }
        else if (devices[i].authenticated)
        {
            int idx = SendMessage(hListPaired, LB_ADDSTRING, 0, (LPARAM)entry);
            SendMessage(hListPaired, LB_SETITEMDATA, idx, (LPARAM)devices[i].address);
        }
        else
        {
            int idx = SendMessage(hListAvailable, LB_ADDSTRING, 0, (LPARAM)entry);
            SendMessage(hListAvailable, LB_SETITEMDATA, idx, (LPARAM)devices[i].address);
        }
    }

    /*wchar_t dbgMsg[1024];
    wsprintf(dbgMsg, L"Получено устройств: %d\nПервое имя: %ls\n", count, count > 0 ? devices[0].name : L"нет");
    MessageBox(NULL, dbgMsg, L"Отладка", MB_OK);*/

    free(devices);
}