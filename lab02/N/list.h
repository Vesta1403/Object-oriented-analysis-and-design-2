#pragma once
#include <windows.h>
#include <bluetoothapis.h>

typedef struct {
    UINT64 address;
    wchar_t name[BLUETOOTH_MAX_NAME_SIZE];
    BOOL authenticated;
    BOOL connected;
    BOOL remembered;
} BluetoothDeviceInfo;

int EnumerateBluetoothDevices(BluetoothDeviceInfo** outDevices, int* outCount);
bool ConnectToBluetoothDevice(HWND hWnd, const BluetoothDeviceInfo* device);
bool DisconnectFromBluetoothDevice(const BluetoothDeviceInfo* device);