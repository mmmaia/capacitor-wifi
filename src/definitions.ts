import type { PluginListenerHandle } from '@capacitor/core';

/**
 * WiFi plugin for managing device WiFi connectivity
 *
 * @since 7.0.0
 */
export interface CapacitorWifiPlugin {
  /**
   * Show a system dialog to add a Wi-Fi network to the device.
   * On Android SDK 30+, this opens the system Wi-Fi settings with the network pre-filled.
   * On iOS, this connects to the network directly.
   *
   * @param options - Network configuration options
   * @returns Promise that resolves when the network is added
   * @throws Error if adding the network fails
   * @since 7.0.0
   * @example
   * ```typescript
   * await CapacitorWifi.addNetwork({
   *   ssid: 'MyNetwork',
   *   password: 'mypassword',
   *   isHiddenSsid: false,
   *   securityType: NetworkSecurityType.WPA2_PSK
   * });
   * ```
   */
  addNetwork(options: AddNetworkOptions): Promise<void>;

  /**
   * Connect to a Wi-Fi network.
   * On Android, this creates a temporary connection that doesn't route traffic through the network by default.
   * For a persistent connection on Android, use addNetwork() instead.
   * On iOS, this creates a persistent connection.
   *
   * @param options - Connection options
   * @returns Promise that resolves when connected
   * @throws Error if connection fails
   * @since 7.0.0
   * @example
   * ```typescript
   * await CapacitorWifi.connect({
   *   ssid: 'MyNetwork',
   *   password: 'mypassword'
   * });
   * ```
   */
  connect(options: ConnectOptions): Promise<void>;

  /**
   * Disconnect from the current Wi-Fi network.
   * On iOS, only disconnects from networks that were added via this plugin.
   *
   * @param options - Optional disconnect options
   * @returns Promise that resolves when disconnected
   * @throws Error if disconnection fails
   * @since 7.0.0
   * @example
   * ```typescript
   * await CapacitorWifi.disconnect();
   * ```
   */
  disconnect(options?: DisconnectOptions): Promise<void>;

  /**
   * Get a list of available Wi-Fi networks from the last scan.
   * Only available on Android.
   *
   * @returns Promise that resolves with the list of networks
   * @throws Error if getting networks fails or on unsupported platform
   * @since 7.0.0
   * @example
   * ```typescript
   * const { networks } = await CapacitorWifi.getAvailableNetworks();
   * networks.forEach(network => {
   *   console.log(`SSID: ${network.ssid}, Signal: ${network.rssi} dBm`);
   * });
   * ```
   */
  getAvailableNetworks(): Promise<GetAvailableNetworksResult>;

  /**
   * Get the device's current IP address.
   * Available on both Android and iOS.
   *
   * @returns Promise that resolves with the IP address
   * @throws Error if getting IP address fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const { ipAddress } = await CapacitorWifi.getIpAddress();
   * console.log('IP Address:', ipAddress);
   * ```
   */
  getIpAddress(): Promise<GetIpAddressResult>;

  /**
   * Get comprehensive IP address information including public and private IPv4/IPv6 addresses.
   * Includes VPN detection and multiple fallback strategies for maximum compatibility.
   * Available on Android.
   *
   * @returns Promise that resolves with IP addresses and VPN status
   * @throws Error if getting IP addresses fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const result = await CapacitorWifi.getIpAddresses();
   * console.log('Private IPv4:', result.ipv4);
   * console.log('Public IPv4:', result.publicIpv4);
   * console.log('Is VPN:', result.isVpn);
   * ```
   */
  getIpAddresses(): Promise<GetIpAddressesResult>;

  /**
   * Get the received signal strength indicator (RSSI) of the current network in dBm.
   * Only available on Android.
   *
   * @returns Promise that resolves with the RSSI value
   * @throws Error if getting RSSI fails or on unsupported platform
   * @since 7.0.0
   * @example
   * ```typescript
   * const { rssi } = await CapacitorWifi.getRssi();
   * console.log('Signal strength:', rssi, 'dBm');
   * ```
   */
  getRssi(): Promise<GetRssiResult>;

  /**
   * Get the service set identifier (SSID) of the current network.
   * Available on both Android and iOS.
   *
   * @returns Promise that resolves with the SSID and VPN status
   * @throws Error if getting SSID fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const { ssid, isVpn } = await CapacitorWifi.getSsid();
   * console.log('Connected to:', ssid);
   * console.log('Is VPN:', isVpn);
   * ```
   */
  getSsid(): Promise<GetSsidResult>;

  /**
   * Get the basic service set identifier (BSSID/MAC address) of the current access point.
   * Available on Android.
   *
   * @returns Promise that resolves with the BSSID and VPN status
   * @throws Error if getting BSSID fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const { bssid, isVpn } = await CapacitorWifi.getBssid();
   * console.log('Connected to AP:', bssid);
   * console.log('Is VPN:', isVpn);
   * ```
   */
  getBssid(): Promise<GetBssidResult>;

  /**
   * Check if Wi-Fi is enabled on the device.
   * Only available on Android.
   *
   * @returns Promise that resolves with the Wi-Fi enabled status
   * @throws Error if checking status fails or on unsupported platform
   * @since 7.0.0
   * @example
   * ```typescript
   * const { enabled } = await CapacitorWifi.isEnabled();
   * console.log('WiFi is', enabled ? 'enabled' : 'disabled');
   * ```
   */
  isEnabled(): Promise<IsEnabledResult>;

  /**
   * Start scanning for Wi-Fi networks.
   * Only available on Android.
   * Results are delivered via the 'networksScanned' event listener.
   * Note: May fail due to system throttling or hardware issues.
   *
   * @returns Promise that resolves when scan starts
   * @throws Error if scan fails or on unsupported platform
   * @since 7.0.0
   * @example
   * ```typescript
   * await CapacitorWifi.addListener('networksScanned', () => {
   *   console.log('Scan completed');
   * });
   * await CapacitorWifi.startScan();
   * ```
   */
  startScan(): Promise<void>;

  /**
   * Check the current permission status for location access.
   * Location permission is required for Wi-Fi operations on both platforms.
   *
   * @returns Promise that resolves with the permission status
   * @throws Error if checking permissions fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const status = await CapacitorWifi.checkPermissions();
   * console.log('Location permission:', status.location);
   * ```
   */
  checkPermissions(): Promise<PermissionStatus>;

  /**
   * Request location permissions from the user.
   * Location permission is required for Wi-Fi operations on both platforms.
   *
   * @param options - Optional permission request options
   * @returns Promise that resolves with the updated permission status
   * @throws Error if requesting permissions fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const status = await CapacitorWifi.requestPermissions();
   * if (status.location === 'granted') {
   *   console.log('Permission granted');
   * }
   * ```
   */
  requestPermissions(options?: RequestPermissionsOptions): Promise<PermissionStatus>;

  /**
   * Add a listener for the 'networksScanned' event.
   * Only available on Android.
   * This event is fired when Wi-Fi scan results are available.
   *
   * @param eventName - The event name ('networksScanned')
   * @param listenerFunc - The callback function to execute
   * @returns Promise that resolves with a listener handle
   * @since 7.0.0
   * @example
   * ```typescript
   * const listener = await CapacitorWifi.addListener('networksScanned', async () => {
   *   const { networks } = await CapacitorWifi.getAvailableNetworks();
   *   console.log('Found networks:', networks);
   * });
   * ```
   */
  addListener(eventName: 'networksScanned', listenerFunc: () => void): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners for this plugin.
   *
   * @returns Promise that resolves when all listeners are removed
   * @since 7.0.0
   * @example
   * ```typescript
   * await CapacitorWifi.removeAllListeners();
   * ```
   */
  removeAllListeners(): Promise<void>;

  /**
   * Get the native plugin version.
   *
   * @returns Promise that resolves with the plugin version
   * @throws Error if getting the version fails
   * @since 7.0.0
   * @example
   * ```typescript
   * const { version } = await CapacitorWifi.getPluginVersion();
   * console.log('Plugin version:', version);
   * ```
   */
  getPluginVersion(): Promise<{ version: string }>;
}

/**
 * Options for adding a network
 *
 * @since 7.0.0
 */
export interface AddNetworkOptions {
  /**
   * The SSID of the network to add
   *
   * @since 7.0.0
   */
  ssid: string;

  /**
   * The password for the network (optional for open networks)
   *
   * @since 7.0.0
   */
  password?: string;

  /**
   * Whether the network is hidden (Android only)
   *
   * @since 7.0.0
   * @default false
   */
  isHiddenSsid?: boolean;

  /**
   * The security type of the network (Android only)
   *
   * @since 7.0.0
   * @default NetworkSecurityType.WPA2_PSK
   */
  securityType?: NetworkSecurityType;
}

/**
 * Options for connecting to a network
 *
 * @since 7.0.0
 */
export interface ConnectOptions {
  /**
   * The SSID of the network to connect to
   *
   * @since 7.0.0
   */
  ssid: string;

  /**
   * The password for the network (optional for open networks)
   *
   * @since 7.0.0
   */
  password?: string;

  /**
   * Whether the network is hidden (Android only)
   *
   * @since 7.0.0
   * @default false
   */
  isHiddenSsid?: boolean;
}

/**
 * Options for disconnecting from a network
 *
 * @since 7.0.0
 */
export interface DisconnectOptions {
  /**
   * The SSID of the network to disconnect from (optional)
   *
   * @since 7.0.0
   */
  ssid?: string;
}

/**
 * Result from getAvailableNetworks()
 *
 * @since 7.0.0
 */
export interface GetAvailableNetworksResult {
  /**
   * List of available networks
   *
   * @since 7.0.0
   */
  networks: Network[];
}

/**
 * Represents a Wi-Fi network
 *
 * @since 7.0.0
 */
export interface Network {
  /**
   * The SSID of the network
   *
   * @since 7.0.0
   */
  ssid: string;

  /**
   * The signal strength in dBm
   *
   * @since 7.0.0
   */
  rssi: number;

  /**
   * The security types supported by this network (Android SDK 33+ only)
   *
   * @since 7.0.0
   */
  securityTypes?: NetworkSecurityType[];
}

/**
 * Result from getIpAddress()
 *
 * @since 7.0.0
 */
export interface GetIpAddressResult {
  /**
   * The device's IP address
   *
   * @since 7.0.0
   */
  ipAddress: string;
}

/**
 * Result from getIpAddresses()
 *
 * @since 7.0.0
 */
export interface GetIpAddressesResult {
  /**
   * Private IPv4 address (e.g., 192.168.1.100)
   *
   * @since 7.0.0
   */
  ipv4?: string;

  /**
   * Public IPv4 address
   *
   * @since 7.0.0
   */
  publicIpv4?: string;

  /**
   * Private IPv6 address
   *
   * @since 7.0.0
   */
  ipv6?: string;

  /**
   * Public IPv6 address
   *
   * @since 7.0.0
   */
  publicIpv6?: string;

  /**
   * Whether the device is connected through a VPN
   *
   * @since 7.0.0
   */
  isVpn: boolean;
}

/**
 * Result from getRssi()
 *
 * @since 7.0.0
 */
export interface GetRssiResult {
  /**
   * The signal strength in dBm
   *
   * @since 7.0.0
   */
  rssi: number;
}

/**
 * Result from getSsid()
 *
 * @since 7.0.0
 */
export interface GetSsidResult {
  /**
   * The SSID of the current network (null if not connected or unknown)
   *
   * @since 7.0.0
   */
  ssid: string | null;

  /**
   * Whether the device is connected through a VPN
   *
   * @since 7.0.0
   */
  isVpn: boolean;
}

/**
 * Result from getBssid()
 *
 * @since 7.0.0
 */
export interface GetBssidResult {
  /**
   * The BSSID (MAC address) of the current access point (null if not connected)
   *
   * @since 7.0.0
   */
  bssid: string | null;

  /**
   * Whether the device is connected through a VPN
   *
   * @since 7.0.0
   */
  isVpn: boolean;
}

/**
 * Result from isEnabled()
 *
 * @since 7.0.0
 */
export interface IsEnabledResult {
  /**
   * Whether Wi-Fi is enabled
   *
   * @since 7.0.0
   */
  enabled: boolean;
}

/**
 * Permission status
 *
 * @since 7.0.0
 */
export interface PermissionStatus {
  /**
   * Location permission state
   *
   * @since 7.0.0
   */
  location: PermissionState;
}

/**
 * Possible permission states
 *
 * @since 7.0.0
 */
export type PermissionState = 'granted' | 'denied' | 'prompt';

/**
 * Options for requesting permissions
 *
 * @since 7.0.0
 */
export interface RequestPermissionsOptions {
  /**
   * Permissions to request
   *
   * @since 7.0.0
   */
  permissions?: 'location'[];
}

/**
 * Network security types
 *
 * @since 7.0.0
 */
export enum NetworkSecurityType {
  /**
   * Open network with no security
   *
   * @since 7.0.0
   */
  OPEN = 0,

  /**
   * WEP security
   *
   * @since 7.0.0
   */
  WEP = 1,

  /**
   * WPA/WPA2 Personal (PSK)
   *
   * @since 7.0.0
   */
  WPA2_PSK = 2,

  /**
   * WPA/WPA2/WPA3 Enterprise (EAP)
   *
   * @since 7.0.0
   */
  EAP = 3,

  /**
   * WPA3 Personal (SAE)
   *
   * @since 7.0.0
   */
  SAE = 4,

  /**
   * WPA3 Enterprise
   *
   * @since 7.0.0
   */
  WPA3_ENTERPRISE = 5,

  /**
   * WPA3 Enterprise 192-bit mode
   *
   * @since 7.0.0
   */
  WPA3_ENTERPRISE_192_BIT = 6,

  /**
   * Passpoint network
   *
   * @since 7.0.0
   */
  PASSPOINT = 7,

  /**
   * Enhanced Open (OWE)
   *
   * @since 7.0.0
   */
  OWE = 8,

  /**
   * WAPI PSK
   *
   * @since 7.0.0
   */
  WAPI_PSK = 9,

  /**
   * WAPI Certificate
   *
   * @since 7.0.0
   */
  WAPI_CERT = 10,
}
