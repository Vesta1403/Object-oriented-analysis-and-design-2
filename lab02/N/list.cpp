#include "list.h"
#include <stdlib.h>
#include <strsafe.h>
#include <bluetoothapis.h>

#pragma comment(lib, "bthprops.lib")

#pragma warning(disable: 4995)

#ifndef BLUETOOTH_AUTHENTICATE_NEW_DEVICE
#define BLUETOOTH_AUTHENTICATE_NEW_DEVICE 0x01
#endif

int EnumerateBluetoothDevices(BluetoothDeviceInfo** outDevices, int* outCount) {
    if (!outDevices || !outCount)
        return ERROR_INVALID_PARAMETER;

    *outDevices = NULL;
    *outCount = 0;

    BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams = { sizeof(searchParams) };
    searchParams.fReturnAuthenticated = TRUE;
    searchParams.fReturnRemembered = TRUE;
    searchParams.fReturnUnknown = TRUE;
    searchParams.fReturnConnected = TRUE;
    searchParams.fIssueInquiry = TRUE;      // активный поиск
    searchParams.cTimeoutMultiplier = 6;    // ~5 секунд
    searchParams.hRadio = NULL;              // все адаптеры

    BLUETOOTH_DEVICE_INFO deviceInfo = { sizeof(deviceInfo) };
    HBLUETOOTH_DEVICE_FIND hFind = BluetoothFindFirstDevice(&searchParams, &deviceInfo);
    if (hFind == NULL) {
        DWORD err = GetLastError();
        if (err == ERROR_NO_MORE_ITEMS)
            return 0;   // нет устройств – не ошибка
        return err;
    }

    int capacity = 10;
    BluetoothDeviceInfo* devices = (BluetoothDeviceInfo*)malloc(capacity * sizeof(BluetoothDeviceInfo));
    if (!devices) {
        BluetoothFindDeviceClose(hFind);
        return ERROR_OUTOFMEMORY;
    }

    int count = 0;
    do {
        if (count >= capacity) {
            capacity *= 2;
            BluetoothDeviceInfo* newDev = (BluetoothDeviceInfo*)realloc(devices, capacity * sizeof(BluetoothDeviceInfo));
            if (!newDev) {
                free(devices);
                BluetoothFindDeviceClose(hFind);
                return ERROR_OUTOFMEMORY;
            }
            devices = newDev;
        }

        BluetoothDeviceInfo* dev = &devices[count];
        dev->address = deviceInfo.Address.ullLong;
        //wcsncpy(dev->name, deviceInfo.szName, BLUETOOTH_MAX_NAME_SIZE);
        StringCchCopy(dev->name, BLUETOOTH_MAX_NAME_SIZE, deviceInfo.szName);
        dev->name[BLUETOOTH_MAX_NAME_SIZE - 1] = L'\0';
        dev->authenticated = deviceInfo.fAuthenticated;
        dev->connected = deviceInfo.fConnected;
        dev->remembered = deviceInfo.fRemembered;
        count++;
    } while (BluetoothFindNextDevice(hFind, &deviceInfo));

    BluetoothFindDeviceClose(hFind);

    // Обрезаем массив до реального размера
    if (count > 0) {
        BluetoothDeviceInfo* trimmed = (BluetoothDeviceInfo*)realloc(devices, count * sizeof(BluetoothDeviceInfo));
        if (trimmed)
            devices = trimmed;
    }
    else {
        free(devices);
        devices = NULL;
    }

    *outDevices = devices;
    *outCount = count;
    return 0;
}

// Подключение к устройству
// -------------------------------------------------------------------
bool ConnectToBluetoothDevice(HWND hWnd, const BluetoothDeviceInfo* device) {
    BLUETOOTH_DEVICE_INFO deviceInfo = { sizeof(deviceInfo) };
    deviceInfo.Address.ullLong = device->address;
    StringCchCopy(deviceInfo.szName, BLUETOOTH_MAX_NAME_SIZE, device->name);

    const GUID A2DP_GUID = { 0x0000110B, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    const GUID AVRCP_GUID = { 0x0000110E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    const GUID HFP_GUID = { 0x0000111E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };

    BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams = { sizeof(searchParams) };
    searchParams.fReturnAuthenticated = TRUE;
    searchParams.fReturnRemembered = TRUE;

    // Проверка сопряжения
    BLUETOOTH_DEVICE_INFO checkInfo = { sizeof(checkInfo) };
    checkInfo.Address.ullLong = device->address;
    HBLUETOOTH_DEVICE_FIND hFind = BluetoothFindFirstDevice(&searchParams, &checkInfo);
    bool isPaired = (hFind != NULL);
    if (hFind) BluetoothFindDeviceClose(hFind);

    if (!isPaired) {
        DWORD result = BluetoothAuthenticateDevice(hWnd, NULL, &deviceInfo, NULL, BLUETOOTH_AUTHENTICATE_NEW_DEVICE);
        if (result != ERROR_SUCCESS) {
            wchar_t msg[128];
            StringCchPrintf(msg, 128, L"Сопряжение не удалось (ошибка %lu)", result);
            MessageBox(hWnd, msg, L"Ошибка", MB_OK);
            return false;
        }

        // Ожидание появления устройства в системе
        bool appeared = false;
        for (int i = 0; i < 20; ++i) {
            Sleep(250);
            BLUETOOTH_DEVICE_INFO waitInfo = { sizeof(waitInfo) };
            waitInfo.Address.ullLong = device->address;
            HBLUETOOTH_DEVICE_FIND hWait = BluetoothFindFirstDevice(&searchParams, &waitInfo);
            if (hWait) {
                BluetoothFindDeviceClose(hWait);
                appeared = true;
                break;
            }
        }
        if (!appeared) {
            MessageBox(hWnd, L"Устройство не появилось после сопряжения", L"Ошибка", MB_OK);
            return false;
        }
    }

    // Включение профиля A2DP (передаём NULL как hRadio)
    DWORD result = BluetoothSetServiceState(NULL, &deviceInfo, &A2DP_GUID, BLUETOOTH_SERVICE_ENABLE);
    if (result != ERROR_SUCCESS) {
        wchar_t msg[256];
        StringCchPrintf(msg, 256, L"Не удалось включить A2DP (ошибка %lu)", result);
        MessageBox(hWnd, msg, L"Ошибка", MB_OK);
        return false;
    }

    // Включение остальных профилей (ошибки игнорируем)
    BluetoothSetServiceState(NULL, &deviceInfo, &AVRCP_GUID, BLUETOOTH_SERVICE_ENABLE);
    BluetoothSetServiceState(NULL, &deviceInfo, &HFP_GUID, BLUETOOTH_SERVICE_ENABLE);

    return true;
}

bool DisconnectFromBluetoothDevice(const BluetoothDeviceInfo* device) {
    BLUETOOTH_DEVICE_INFO deviceInfo = { sizeof(deviceInfo) };
    deviceInfo.Address.ullLong = device->address;
    StringCchCopy(deviceInfo.szName, BLUETOOTH_MAX_NAME_SIZE, device->name);

    const GUID A2DP_GUID = { 0x0000110B, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    const GUID AVRCP_GUID = { 0x0000110E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    const GUID HFP_GUID = { 0x0000111E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };

    // Пытаемся отключить все три профиля
    DWORD result = BluetoothSetServiceState(NULL, &deviceInfo, &A2DP_GUID, BLUETOOTH_SERVICE_DISABLE);
    if (result != ERROR_SUCCESS) {
        wchar_t msg[256];
        StringCchPrintf(msg, 256, L"Не удалось отключить A2DP (ошибка %lu)", result);
        MessageBox(NULL, msg, L"Ошибка", MB_OK);
        return false;
    }

    BluetoothSetServiceState(NULL, &deviceInfo, &AVRCP_GUID, BLUETOOTH_SERVICE_DISABLE);
    BluetoothSetServiceState(NULL, &deviceInfo, &HFP_GUID, BLUETOOTH_SERVICE_DISABLE);

    return true;
}