# Example App for `@capgo/capacitor-wifi`

This Vite project links directly to the local plugin source so you can exercise the native APIs while developing.

## Actions in this playground

- **Get plugin version** – Calls getPluginVersion() to get the native plugin version.
- **Check permissions** – Checks the current location permission status (required for WiFi operations).
- **Request permissions** – Requests location permissions from the user (required for WiFi operations).
- **Get current SSID** – Gets the SSID of the currently connected WiFi network.
- **Get IP address** – Gets the device's current IP address.
- **Get RSSI (Android)** – Gets the received signal strength indicator (RSSI) of the current network in dBm. Android only.
- **Check if WiFi enabled (Android)** – Checks if WiFi is enabled on the device. Android only.
- **Start network scan (Android)** – Starts scanning for available WiFi networks. Android only. Results delivered via networksScanned event.
- **Get available networks (Android)** – Gets the list of available WiFi networks from the last scan. Android only.
- **Connect to network** – Connects to a WiFi network. Creates temporary connection on Android, persistent on iOS.
- **Add network** – Shows system dialog to add a WiFi network (Android SDK 30+) or connects directly (iOS).
- **Disconnect from network** – Disconnects from the current WiFi network. On iOS, only disconnects networks added via this plugin.

## Getting started

```bash
npm install
npm start
```

Add native shells with `npx cap add ios` or `npx cap add android` from this folder to try behaviour on device or simulator.
