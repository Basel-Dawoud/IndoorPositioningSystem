# Indoor Positioning System using BLE Beacons and ESP32

## Project Overview
This project implements a real-time indoor positioning system (IPS) using BLE beacons and an ESP32. BLE-PB03M beacons broadcast signals, which are collected by a mobile application. RSSI data is sent via an MQTT broker (running on a laptop) to an ESP32, which calculates the user’s \((x, y)\) position using trilateration and sends the result back to the mobile app.  

## System Components
- **Beacons**: BLE-PB03M devices broadcasting their presence.
- **Mobile App**: Collects RSSI data from beacons and communicates with the ESP32 through the MQTT broker.
- **ESP32**: Receives beacon data from the MQTT broker, calculates the position, and returns the coordinates.
- **MQTT Broker**: Mosquitto broker running on a laptop, enabling communication between the mobile app and the ESP32.

## How It Works
1. Beacons continuously broadcast BLE signals.  
2. The mobile app detects nearby beacons and collects their RSSI values.  
3. The app sends RSSI data to the ESP32 via the MQTT broker.  
4. ESP32 calculates the user's position using trilateration based on RSSI.  
5. ESP32 sends the calculated position back to the app via the MQTT broker.  
<img width="1400" height="950" alt="ble-beacon-system-diagram" src="https://github.com/user-attachments/assets/b5651d7b-1ae2-40c1-a4df-bb9b64fa36a1" />


## Setup Instructions
1. **MQTT Broker**: Install Mosquitto on your laptop and run it to handle communication.  
2. **ESP32 Firmware**: Flash the ESP32 with the provided code to enable MQTT communication and position calculation.  
3. **Mobile App**: Install the app that collects beacon data and communicates with the broker.  
4. **Beacons**: Configure each BLE-PB03M beacon with a unique UUID and broadcast settings.  

## Notes
- Ensure the mobile device and ESP32 are connected to the same network as the MQTT broker.  
- Calibrate RSSI values for better positioning accuracy.  
