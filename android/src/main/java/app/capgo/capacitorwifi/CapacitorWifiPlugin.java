package app.capgo.capacitorwifi;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.provider.Settings;
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
        android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();

        // Remove quotes if present
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        JSObject result = new JSObject();
        result.put("ssid", ssid);
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
