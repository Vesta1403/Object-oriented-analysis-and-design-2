#include <windows.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Devices.Radios.h>

#include <winrt/Windows.Devices.Bluetooth.h>
#include <winrt/Windows.Devices.Enumeration.h>


#include "Bl.h"

using namespace winrt;
using namespace Windows::Devices::Radios;
using namespace Windows::Devices::Enumeration;  
using namespace std;



static Radio g_bluetoothRadio = nullptr;

bool InitBluetooth() // Найти Bluetooth-адаптер
{
    try
    {
        auto accessStatus = Radio::RequestAccessAsync().get();
        if (accessStatus != RadioAccessStatus::Allowed)
            return false;

        auto radios = Radio::GetRadiosAsync().get();
        for (auto const& radio : radios)
        {
            if (radio.Kind() == RadioKind::Bluetooth)
            {
                g_bluetoothRadio = radio;
                return true;
            }
        }
    }
    catch (winrt::hresult_error const& ex)
    {
        OutputDebugStringW(ex.message().c_str());
    }
    return false;
}

bool IsBluetoothEnabled() // Состояние
{
    if (!g_bluetoothRadio)
        return false;
    return g_bluetoothRadio.State() == RadioState::On;
}

bool SetBluetoothEnabled(bool enable) //
{
    if (!g_bluetoothRadio)
        return false;
    try
    {
        RadioState newState = enable ? RadioState::On : RadioState::Off;
        auto result = g_bluetoothRadio.SetStateAsync(newState).get();
        return result == RadioAccessStatus::Allowed;
    }
    catch (...)
    {
        return false;
    }
}
