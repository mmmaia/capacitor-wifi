package app.capgo.capacitorwifi;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

@CapacitorPlugin(
    name = "CapacitorWifi",
    permissions = {
        @Permission(
            alias = "location",
            strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            }
        )
    }
)
public class CapacitorWifiPlugin extends Plugin {

    private final String pluginVersion = "7.0.0";
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private BroadcastReceiver scanResultsReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public void load() {
        wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        // Set up scan results receiver
        scanResultsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyListeners("networksScanned", new JSObject());
            }
        };
    }

    @PluginMethod
    public void addNetwork(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - use system dialog
            addNetworkModern(call);
        } else {
            // Pre-Android 10 - programmatic approach
            addNetworkLegacy(call);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void addNetworkModern(PluginCall call) {
        String ssid = call.getString("ssid");
        if (ssid == null) {
            call.reject("SSID is required");
            return;
        }

        // Open WiFi settings with SSID
        Intent intent = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
        intent.putExtra(Settings.EXTRA_WIFI_NETWORK_LIST, new String[] { ssid });
        getActivity().startActivity(intent);
        call.resolve();
    }

    private void addNetworkLegacy(PluginCall call) {
        String ssid = call.getString("ssid");
        String password = call.getString("password");
        Boolean isHidden = call.getBoolean("isHiddenSsid", false);

        if (ssid == null) {
            call.reject("SSID is required");
            return;
        }

        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "addNetworkCallback");
            return;
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.hiddenSSID = isHidden;

        if (password != null && !password.isEmpty()) {
            config.preSharedKey = "\"" + password + "\"";
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        int netId = wifiManager.addNetwork(config);
        if (netId == -1) {
            call.reject("Failed to add network");
            return;
        }

        boolean enabled = wifiManager.enableNetwork(netId, true);
        if (!enabled) {
            call.reject("Failed to enable network");
            return;
        }

        call.resolve();
    }

    @PermissionCallback
    private void addNetworkCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            addNetworkLegacy(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectModern(call);
        } else {
            connectLegacy(call);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectModern(PluginCall call) {
        String ssid = call.getString("ssid");
        String password = call.getString("password");

        if (ssid == null) {
            call.reject("SSID is required");
            return;
        }

        WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder().setSsid(ssid);

        if (password != null && !password.isEmpty()) {
            specifierBuilder.setWpa2Passphrase(password);
        }

        NetworkSpecifier specifier = specifierBuilder.build();

        NetworkRequest request = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                call.resolve();
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                call.reject("Network unavailable");
            }
        };

        connectivityManager.requestNetwork(request, networkCallback);
    }

    private void connectLegacy(PluginCall call) {
        addNetworkLegacy(call);
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            }
        } else {
            wifiManager.disconnect();
        }
        call.resolve();
    }

    @PluginMethod
    public void getAvailableNetworks(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getAvailableNetworksCallback");
            return;
        }

        getAvailableNetworksWithPermission(call);
    }

    @PermissionCallback
    private void getAvailableNetworksCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getAvailableNetworksWithPermission(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    private void getAvailableNetworksWithPermission(PluginCall call) {
        List<ScanResult> results = wifiManager.getScanResults();
        JSArray networks = new JSArray();

        for (ScanResult result : results) {
            JSObject network = new JSObject();
            network.put("ssid", result.SSID);
            network.put("rssi", result.level);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                JSArray securityTypes = new JSArray();
                // Add security types based on capabilities
                String capabilities = result.capabilities;
                if (capabilities.contains("WPA3")) {
                    securityTypes.put(4); // SAE
                } else if (capabilities.contains("WPA2")) {
                    securityTypes.put(2); // WPA2_PSK
                } else if (capabilities.contains("WPA")) {
                    securityTypes.put(2); // WPA2_PSK
                } else if (capabilities.contains("WEP")) {
                    securityTypes.put(1); // WEP
                } else {
                    securityTypes.put(0); // OPEN
                }
                network.put("securityTypes", securityTypes);
            }

            networks.put(network);
        }

        JSObject result = new JSObject();
        result.put("networks", networks);
        call.resolve(result);
    }

    @PluginMethod
    public void getIpAddress(PluginCall call) {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        String ip = inetAddress.getHostAddress();
                        JSObject result = new JSObject();
                        result.put("ipAddress", ip);
                        call.resolve(result);
                        return;
                    }
                }
            }
            call.reject("No IP address found");
        } catch (Exception e) {
            call.reject("Failed to get IP address", e);
        }
    }

    private boolean isPrivateIPv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] ip = address.getAddress();
        // 10.0.0.0/8
        if (ip[0] == 10) return true;
        // 172.16.0.0/12
        if (ip[0] == (byte)172 && (ip[1] & 0xF0) == 16) return true;
        // 192.168.0.0/16
        if (ip[0] == (byte)192 && ip[1] == (byte)168) return true;
        // 127.0.0.0/8 (loopback)
        if (ip[0] == 127) return true;
        return false;
    }

    private boolean isPrivateIPv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        // Link-local (fe80::/10) and loopback (::1) are private
        return address.isLinkLocalAddress() || address.isLoopbackAddress();
    }

    @PluginMethod
    public void getIpAddresses(PluginCall call) {
        JSObject result = new JSObject();
        String TAG = "CapacitorWifi";
        Log.d(TAG, "getIpAddresses: Starting IP address collection");

        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                Log.w(TAG, "getIpAddresses: ConnectivityManager is null");
                call.resolve(result);
                return;
            }

            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) {
                Log.w(TAG, "getIpAddresses: No active network");
                call.resolve(result);
                return;
            }

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            if (capabilities != null) {
                boolean isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                result.put("isVpn", isVpn);
                Log.d(TAG, "getIpAddresses: isVpn = " + isVpn);
            }

            LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
            if (linkProperties != null) {
                List<LinkAddress> addresses = linkProperties.getLinkAddresses();
                Log.d(TAG, "getIpAddresses: LinkProperties has " + addresses.size() + " addresses");
                for (LinkAddress address : addresses) {
                    InetAddress inetAddress = address.getAddress();
                    String ipAddr = inetAddress.getHostAddress();

                    if (inetAddress instanceof Inet4Address) {
                        if (isPrivateIPv4(inetAddress)) {
                            result.put("ipv4", ipAddr);
                            Log.d(TAG, "getIpAddresses: Found private IPv4: " + ipAddr);
                        } else {
                            result.put("publicIpv4", ipAddr);
                            Log.d(TAG, "getIpAddresses: Found public IPv4: " + ipAddr);
                        }
                    } else if (inetAddress instanceof Inet6Address && !inetAddress.isLinkLocalAddress()) {
                        if (isPrivateIPv6(inetAddress)) {
                            result.put("ipv6", ipAddr);
                            Log.d(TAG, "getIpAddresses: Found private IPv6: " + ipAddr);
                        } else {
                            result.put("publicIpv6", ipAddr);
                            Log.d(TAG, "getIpAddresses: Found public IPv6: " + ipAddr);
                        }
                    }
                }
            } else {
                Log.w(TAG, "getIpAddresses: LinkProperties is null");
            }

            // Fallback for devices that don't return IPv4/IPv6 via LinkProperties
            if (!result.has("ipv4") && !result.has("publicIpv4")) {
                Log.d(TAG, "getIpAddresses: Using WifiManager fallback for IPv4");
                WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    if (info != null) {
                        int ipInt = info.getIpAddress();
                        Log.d(TAG, "getIpAddresses: WifiInfo.getIpAddress() returned: " + ipInt);
                        if (ipInt != 0) {
                            // Convert little-endian int to dotted IPv4 string
                            String ipv4 = String.format(
                                "%d.%d.%d.%d",
                                (ipInt & 0xff),
                                (ipInt >> 8 & 0xff),
                                (ipInt >> 16 & 0xff),
                                (ipInt >> 24 & 0xff)
                            );
                            // WifiManager typically returns private IPs
                            result.put("ipv4", ipv4);
                            Log.d(TAG, "getIpAddresses: Found private IPv4 from WifiManager: " + ipv4);
                        }
                    } else {
                        Log.w(TAG, "getIpAddresses: WifiInfo is null");
                    }
                } else {
                    Log.w(TAG, "getIpAddresses: WifiManager is null");
                }
            }

            // Final fallback: iterate network interfaces for non-loopback addresses
            boolean needsIpv4 = !result.has("ipv4") && !result.has("publicIpv4");
            boolean needsIpv6 = !result.has("ipv6") && !result.has("publicIpv6");
            if (needsIpv4 || needsIpv6) {
                Log.d(TAG, "getIpAddresses: Using NetworkInterface fallback (needs ipv4: " + needsIpv4 + ", needs ipv6: " + needsIpv6 + ")");
                try {
                    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                    if (interfaces == null) {
                        Log.w(TAG, "getIpAddresses: NetworkInterface.getNetworkInterfaces() returned null");
                    } else {
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface networkInterface = interfaces.nextElement();
                            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                            while (inetAddresses.hasMoreElements()) {
                                InetAddress inetAddress = inetAddresses.nextElement();
                                if (inetAddress.isLoopbackAddress()) {
                                    continue;
                                }

                                String ipAddr = inetAddress.getHostAddress();

                                if (inetAddress instanceof Inet4Address && needsIpv4) {
                                    if (isPrivateIPv4(inetAddress)) {
                                        if (!result.has("ipv4")) {
                                            result.put("ipv4", ipAddr);
                                            Log.d(TAG, "getIpAddresses: Found private IPv4 from NetworkInterface: " + ipAddr);
                                        }
                                    } else {
                                        if (!result.has("publicIpv4")) {
                                            result.put("publicIpv4", ipAddr);
                                            Log.d(TAG, "getIpAddresses: Found public IPv4 from NetworkInterface: " + ipAddr);
                                        }
                                    }
                                } else if (inetAddress instanceof Inet6Address && !inetAddress.isLinkLocalAddress() && needsIpv6) {
                                    if (isPrivateIPv6(inetAddress)) {
                                        if (!result.has("ipv6")) {
                                            result.put("ipv6", ipAddr);
                                            Log.d(TAG, "getIpAddresses: Found private IPv6 from NetworkInterface: " + ipAddr);
                                        }
                                    } else {
                                        if (!result.has("publicIpv6")) {
                                            result.put("publicIpv6", ipAddr);
                                            Log.d(TAG, "getIpAddresses: Found public IPv6 from NetworkInterface: " + ipAddr);
                                        }
                                    }
                                }
                            }

                            if ((result.has("ipv4") || result.has("publicIpv4")) &&
                                (result.has("ipv6") || result.has("publicIpv6"))) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getIpAddresses: NetworkInterface fallback failed", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getIpAddresses: Overall method failed", e);
        }

        String logMsg = "getIpAddresses: Returning result with ";
        logMsg += result.has("ipv4") ? "private IPv4, " : "";
        logMsg += result.has("publicIpv4") ? "public IPv4, " : "";
        logMsg += result.has("ipv6") ? "private IPv6, " : "";
        logMsg += result.has("publicIpv6") ? "public IPv6" : "";
        Log.d(TAG, logMsg);
        call.resolve(result);
    }

    @PluginMethod
    public void getBssid(PluginCall call) {
        JSObject result = new JSObject();
        String TAG = "CapacitorWifi";
        Log.d(TAG, "getBssid: Starting BSSID collection");

        if (getPermissionState("location") != PermissionState.GRANTED) {
            Log.w(TAG, "getBssid: Location permission not granted");
            result.put("bssid", JSObject.NULL);
            result.put("isVpn", false);
            call.resolve(result);
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (cm == null || wifiManager == null) {
            Log.w(TAG, "getBssid: ConnectivityManager or WifiManager is null");
            call.resolve(result);
            return;
        }

        Network activeNetwork = cm.getActiveNetwork();
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        boolean isVpn = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);

        WifiInfo info = wifiManager.getConnectionInfo();
        if (info != null) {
            String bssid = info.getBSSID();
            Log.d(TAG, "getBssid: WifiInfo.getBSSID() returned: " + bssid);
            if (bssid != null && !bssid.equals("00:00:00:00:00:00") && !bssid.equals("02:00:00:00:00:00")) {
                result.put("bssid", bssid);
                Log.d(TAG, "getBssid: Valid BSSID found: " + bssid);
            } else {
                result.put("bssid", JSObject.NULL);
                Log.w(TAG, "getBssid: BSSID is null or placeholder value: " + bssid);
            }
        } else {
            Log.w(TAG, "getBssid: WifiInfo is null");
        }
        result.put("isVpn", isVpn);

        String bssidLog = "null";
        if (result.has("bssid")) {
            try {
                Object bssidValue = result.get("bssid");
                if (bssidValue != JSObject.NULL) {
                    bssidLog = bssidValue.toString();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        Log.d(TAG, "getBssid: Returning result with bssid = " + bssidLog);
        call.resolve(result);
    }

    @PluginMethod
    public void getRssi(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getRssiCallback");
            return;
        }

        getRssiWithPermission(call);
    }

    @PermissionCallback
    private void getRssiCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getRssiWithPermission(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    private void getRssiWithPermission(PluginCall call) {
        android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int rssi = wifiInfo.getRssi();

        JSObject result = new JSObject();
        result.put("rssi", rssi);
        call.resolve(result);
    }

    @PluginMethod
    public void getSsid(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getSsidCallback");
            return;
        }

        getSsidWithPermission(call);
    }

    @PermissionCallback
    private void getSsidCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getSsidWithPermission(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    private void getSsidWithPermission(PluginCall call) {
        JSObject result = new JSObject();

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (cm == null || wifiManager == null) {
            call.resolve(result);
            return;
        }

        Network activeNetwork = cm.getActiveNetwork();
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        boolean isVpn = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);

        WifiInfo info = wifiManager.getConnectionInfo();
        if (info != null) {
            String ssid = info.getSSID();
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            // Ignore placeholder/invalid values to reduce nulls downstream
            if (ssid != null && !ssid.equals("<unknown ssid>")) {
                result.put("ssid", ssid);
            } else {
                result.put("ssid", JSObject.NULL);
            }
        }
        result.put("isVpn", isVpn);

        call.resolve(result);
    }

    @PluginMethod
    public void isEnabled(PluginCall call) {
        boolean enabled = wifiManager.isWifiEnabled();

        JSObject result = new JSObject();
        result.put("enabled", enabled);
        call.resolve(result);
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "startScanCallback");
            return;
        }

        startScanWithPermission(call);
    }

    @PermissionCallback
    private void startScanCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            startScanWithPermission(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    private void startScanWithPermission(PluginCall call) {
        // Register receiver
        getContext().registerReceiver(scanResultsReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        boolean success = wifiManager.startScan();
        if (success) {
            call.resolve();
        } else {
            call.reject("Failed to start scan");
        }
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject result = new JSObject();
        result.put("location", getPermissionState("location").toString().toLowerCase());
        call.resolve(result);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            JSObject result = new JSObject();
            result.put("location", "granted");
            call.resolve(result);
        } else {
            requestPermissionForAlias("location", call, "permissionsCallback");
        }
    }

    @PermissionCallback
    private void permissionsCallback(PluginCall call) {
        JSObject result = new JSObject();
        result.put("location", getPermissionState("location").toString().toLowerCase());
        call.resolve(result);
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject result = new JSObject();
            result.put("version", this.pluginVersion);
            call.resolve(result);
        } catch (final Exception e) {
            call.reject("Could not get plugin version", e);
        }
    }

    @Override
    protected void handleOnDestroy() {
        try {
            if (scanResultsReceiver != null) {
                getContext().unregisterReceiver(scanResultsReceiver);
            }
            if (networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            // Receiver might not be registered
        }
        super.handleOnDestroy();
    }
}
