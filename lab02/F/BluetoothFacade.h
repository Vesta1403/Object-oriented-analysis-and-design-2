#pragma once

#include <windows.h>
#include <bluetoothapis.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Devices.Radios.h>
#include <string>
#include <vector>
#include <stdlib.h>


#include <winrt/Windows.Devices.Bluetooth.h>
#include <winrt/Windows.Devices.Enumeration.h>

struct BluetoothDeviceInfo {
    UINT64 address;
    wchar_t name[BLUETOOTH_MAX_NAME_SIZE];
    BOOL authenticated;
    BOOL connected;
    BOOL remembered;
};

class BluetoothFacade {
public:
    BluetoothFacade();
    ~BluetoothFacade();

    bool IsEnabled() const;
    bool SetEnabled(bool enabled);

    std::vector<BluetoothDeviceInfo> ScanDevices();

    bool Connect(const BluetoothDeviceInfo& device, HWND parent = nullptr);
    bool Disconnect(const BluetoothDeviceInfo& device);

    bool ConnectSelected(HWND listBox, HWND parent = nullptr);
    bool DisconnectSelected(HWND listBox);

    std::wstring LastError() const;

private:
    bool m_initialized;
    std::wstring m_lastError;

    // Инициализация nullptr – тип Radio поддерживает такое присваивание
    winrt::Windows::Devices::Radios::Radio m_bluetoothRadio{ nullptr };

    bool InitBluetooth();
    bool IsBluetoothEnabled() const;
    bool SetBluetoothEnabled(bool enable);

    int EnumerateBluetoothDevices(BluetoothDeviceInfo** outDevices, int* outCount);
    bool ConnectToBluetoothDevice(HWND parent, const BluetoothDeviceInfo* device);
    bool DisconnectFromBluetoothDevice(const BluetoothDeviceInfo* device);
};