#include "BluetoothFacade.h"
#include <strsafe.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Devices.Enumeration.h>

#pragma warning(disable: 4996)
#include <bluetoothapis.h>
#pragma comment(lib, "bthprops.lib")
#pragma comment(lib, "runtimeobject.lib")

using namespace winrt;
using namespace Windows::Devices::Radios;
using namespace Windows::Devices::Enumeration;

#ifndef BLUETOOTH_AUTHENTICATE_NEW_DEVICE
#define BLUETOOTH_AUTHENTICATE_NEW_DEVICE 0x01
#endif

BluetoothFacade::BluetoothFacade()
    : m_initialized(false)
{
    try {
        winrt::init_apartment();
    }
    catch (...) { }

    m_initialized = InitBluetooth();
    if (!m_initialized)
        m_lastError = L"Не удалось инициализировать Bluetooth-адаптер.";
}

BluetoothFacade::~BluetoothFacade() { }

bool BluetoothFacade::InitBluetooth()
{
    try {
        auto accessStatus = Radio::RequestAccessAsync().get();
        if (accessStatus != RadioAccessStatus::Allowed)
            return false;

        auto radios = Radio::GetRadiosAsync().get();
        for (auto const& radio : radios) {
            if (radio.Kind() == RadioKind::Bluetooth) {
                m_bluetoothRadio = radio;
                return true;
            }
        }
    }
    catch (winrt::hresult_error const& ex) {
        OutputDebugStringW(ex.message().c_str());
    }
    return false;
}

bool BluetoothFacade::IsBluetoothEnabled() const
{
    if (!m_initialized || !m_bluetoothRadio)
        return false;
    return m_bluetoothRadio.State() == RadioState::On;
}

bool BluetoothFacade::SetBluetoothEnabled(bool enable)
{
    if (!m_initialized || !m_bluetoothRadio)
        return false;
    try {
        RadioState newState = enable ? RadioState::On : RadioState::Off;
        auto result = m_bluetoothRadio.SetStateAsync(newState).get();
        return result == RadioAccessStatus::Allowed;
    }
    catch (...) {
        return false;
    }
}


int BluetoothFacade::EnumerateBluetoothDevices(BluetoothDeviceInfo** outDevices, int* outCount)
{
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
        StringCchCopy(dev->name, BLUETOOTH_MAX_NAME_SIZE, deviceInfo.szName);
        dev->name[BLUETOOTH_MAX_NAME_SIZE - 1] = L'\0';
        dev->authenticated = deviceInfo.fAuthenticated;
        dev->connected = deviceInfo.fConnected;
        dev->remembered = deviceInfo.fRemembered;
        count++;
    } while (BluetoothFindNextDevice(hFind, &deviceInfo));

    BluetoothFindDeviceClose(hFind);

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

std::vector<BluetoothDeviceInfo> BluetoothFacade::ScanDevices()
{
    std::vector<BluetoothDeviceInfo> result;
    if (!m_initialized || !IsBluetoothEnabled()) {
        m_lastError = L"Bluetooth выключен или не инициализирован.";
        return result;
    }

    BluetoothDeviceInfo* devices = nullptr;
    int count = 0;
    int err = EnumerateBluetoothDevices(&devices, &count);
    if (err != 0) {
        wchar_t buf[128];
        StringCchPrintf(buf, 128, L"Ошибка при сканировании: %d", err);
        m_lastError = buf;
        return result;
    }

    result.reserve(count);
    for (int i = 0; i < count; ++i)
        result.push_back(devices[i]);

    free(devices);
    m_lastError.clear();
    return result;
}

bool BluetoothFacade::ConnectToBluetoothDevice(HWND parent, const BluetoothDeviceInfo* device)
{
    BLUETOOTH_DEVICE_INFO deviceInfo = { sizeof(deviceInfo) };
    deviceInfo.Address.ullLong = device->address;
    StringCchCopy(deviceInfo.szName, BLUETOOTH_MAX_NAME_SIZE, device->name);

    
    static const GUID A2DP_GUID = { 0x0000110B, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    static const GUID AVRCP_GUID = { 0x0000110E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    static const GUID HFP_GUID = { 0x0000111E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };

    BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams = { sizeof(searchParams) };
    searchParams.fReturnAuthenticated = TRUE;
    searchParams.fReturnRemembered = TRUE;

    // Проверка, сопряжено ли уже устройство
    BLUETOOTH_DEVICE_INFO checkInfo = { sizeof(checkInfo) };
    checkInfo.Address.ullLong = device->address;
    HBLUETOOTH_DEVICE_FIND hFind = BluetoothFindFirstDevice(&searchParams, &checkInfo);
    bool isPaired = (hFind != NULL);
    if (hFind) BluetoothFindDeviceClose(hFind);

    // Сопряжение, если необходимо
    if (!isPaired)
    {

        DWORD authResult = BluetoothAuthenticateDevice(parent, NULL, &deviceInfo, NULL, BLUETOOTH_AUTHENTICATE_NEW_DEVICE);

        

        if (authResult != ERROR_SUCCESS)
        {
            wchar_t msg[128];
            StringCchPrintf(msg, 128, L"Сопряжение не удалось (ошибка %lu)", authResult);
            m_lastError = msg;
            return false;
        }

        // Ожидание появления устройства после сопряжения
        bool appeared = false;
        for (int i = 0; i < 20; ++i)
        {
            Sleep(250);
            BLUETOOTH_DEVICE_INFO waitInfo = { sizeof(waitInfo) };
            waitInfo.Address.ullLong = device->address;
            HBLUETOOTH_DEVICE_FIND hWait = BluetoothFindFirstDevice(&searchParams, &waitInfo);
            if (hWait)
            {
                BluetoothFindDeviceClose(hWait);
                appeared = true;
                break;
            }
        }
        if (!appeared)
        {
            m_lastError = L"Устройство не появилось после сопряжения";
            return false;
        }
    }

    // Включение профиля A2DP (основной аудиопрофиль)
    DWORD serviceResult = BluetoothSetServiceState(NULL, &deviceInfo, &A2DP_GUID, BLUETOOTH_SERVICE_ENABLE);
    if (serviceResult != ERROR_SUCCESS)
    {
        wchar_t msg[256];
        StringCchPrintf(msg, 256, L"Не удалось включить A2DP (ошибка %lu)", serviceResult);
        m_lastError = msg;
        return false;
    }

    // Включение дополнительных профилей (AVRCP, HFP) – ошибки не критичны
    serviceResult = BluetoothSetServiceState(NULL, &deviceInfo, &AVRCP_GUID, BLUETOOTH_SERVICE_ENABLE);
    if (serviceResult != ERROR_SUCCESS)
    {
        OutputDebugString(L"Предупреждение: не удалось включить AVRCP\n");
    }
    serviceResult = BluetoothSetServiceState(NULL, &deviceInfo, &HFP_GUID, BLUETOOTH_SERVICE_ENABLE);
    if (serviceResult != ERROR_SUCCESS)
    {
        OutputDebugString(L"Предупреждение: не удалось включить HFP\n");
    }

    m_lastError.clear();
    return true;
}

bool BluetoothFacade::DisconnectFromBluetoothDevice(const BluetoothDeviceInfo* device)
{
    BLUETOOTH_DEVICE_INFO deviceInfo = { sizeof(deviceInfo) };
    deviceInfo.Address.ullLong = device->address;
    StringCchCopy(deviceInfo.szName, BLUETOOTH_MAX_NAME_SIZE, device->name);

    const GUID A2DP_GUID = { 0x0000110B, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    const GUID AVRCP_GUID = { 0x0000110E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };
    const GUID HFP_GUID = { 0x0000111E, 0x0000, 0x1000, { 0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB } };

    DWORD result = BluetoothSetServiceState(NULL, &deviceInfo, &A2DP_GUID, BLUETOOTH_SERVICE_DISABLE);
    if (result != ERROR_SUCCESS) {
        wchar_t msg[256];
        StringCchPrintf(msg, 256, L"Не удалось отключить A2DP (ошибка %lu)", result);
        m_lastError = msg;
        return false;
    }

    BluetoothSetServiceState(NULL, &deviceInfo, &AVRCP_GUID, BLUETOOTH_SERVICE_DISABLE);
    BluetoothSetServiceState(NULL, &deviceInfo, &HFP_GUID, BLUETOOTH_SERVICE_DISABLE);

    m_lastError.clear();
    return true;
}

bool BluetoothFacade::Connect(const BluetoothDeviceInfo& device, HWND parent)
{
    if (!m_initialized || !IsBluetoothEnabled()) {
        m_lastError = L"Bluetooth выключен или не инициализирован.";
        return false;
    }
    return ConnectToBluetoothDevice(parent, &device);
}

bool BluetoothFacade::Disconnect(const BluetoothDeviceInfo& device)
{
    if (!m_initialized || !IsBluetoothEnabled()) {
        m_lastError = L"Bluetooth выключен или не инициализирован.";
        return false;
    }
    return DisconnectFromBluetoothDevice(&device);
}

// ============================================================
// Вспомогательные функции для GUI
static UINT64 ExtractAddressFromListBox(HWND hList, int idx)
{
    LRESULT data = SendMessage(hList, LB_GETITEMDATA, idx, 0);
    if (data != LB_ERR && data != 0)
        return static_cast<UINT64>(data);

    wchar_t text[512];
    SendMessage(hList, LB_GETTEXT, idx, reinterpret_cast<LPARAM>(text));
    wchar_t* openBracket = wcschr(text, L'[');
    if (openBracket) {
        wchar_t* closeBracket = wcschr(openBracket, L']');
        if (closeBracket) {
            *closeBracket = L'\0';
            wchar_t* macStr = openBracket + 1;
            UINT64 address = 0;
            for (int i = 0; macStr[i]; ++i) {
                wchar_t c = macStr[i];
                UINT64 digit;
                if (c >= L'0' && c <= L'9') digit = c - L'0';
                else if (c >= L'A' && c <= L'F') digit = c - L'A' + 10;
                else if (c >= L'a' && c <= L'f') digit = c - L'a' + 10;
                else continue;
                address = (address << 4) | digit;
            }
            return address;
        }
    }
    return 0;
}

bool BluetoothFacade::ConnectSelected(HWND listBox, HWND parent)
{
    if (!listBox) {
        m_lastError = L"Не указан список устройств.";
        return false;
    }

    int idx = static_cast<int>(SendMessage(listBox, LB_GETCURSEL, 0, 0));
    if (idx == LB_ERR) {
        m_lastError = L"Ничего не выбрано.";
        return false;
    }

    UINT64 address = ExtractAddressFromListBox(listBox, idx);
    if (address == 0) {
        m_lastError = L"Не удалось определить MAC-адрес выбранного устройства.";
        return false;
    }

    BluetoothDeviceInfo device = {};
    device.address = address;
    return Connect(device, parent);
}

bool BluetoothFacade::DisconnectSelected(HWND listBox)
{
    if (!listBox) {
        m_lastError = L"Не указан список устройств.";
        return false;
    }

    int idx = static_cast<int>(SendMessage(listBox, LB_GETCURSEL, 0, 0));
    if (idx == LB_ERR) {
        m_lastError = L"Ничего не выбрано.";
        return false;
    }

    UINT64 address = ExtractAddressFromListBox(listBox, idx);
    if (address == 0) {
        m_lastError = L"Не удалось определить MAC-адрес выбранного устройства.";
        return false;
    }

    BluetoothDeviceInfo device = {};
    device.address = address;
    return Disconnect(device);
}

std::wstring BluetoothFacade::LastError() const
{
    return m_lastError;
}
bool BluetoothFacade::IsEnabled() const {
    return IsBluetoothEnabled();
}

bool BluetoothFacade::SetEnabled(bool enable) {
    return SetBluetoothEnabled(enable);
}