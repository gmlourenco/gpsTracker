'use client';

import React from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { DeviceWithLatestLocation } from '../types/telemetry';

// Fix Leaflet's default icon path issues with Webpack/Next.js
// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Custom icon for active devices
const activeIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

// Custom icon for offline devices
const offlineIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-grey.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

// Custom icon for SOS devices
const sosIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

interface MapProps {
  devices: DeviceWithLatestLocation[];
}

export default function Map({ devices }: MapProps) {
  // Center map on Portugal by default, or on the first device if available
  const defaultCenter: [number, number] = [39.3999, -8.2245];
  // eslint-disable-next-line react-hooks/purity
  const now = Date.now(); // Cache the current time to avoid calling impure function repeatedly
  
  const validDevices = devices.filter(d => d.latestLocation && d.latestLocation.lat !== undefined && d.latestLocation.lng !== undefined);
  const center: [number, number] = validDevices.length > 0 
    ? [validDevices[0].latestLocation!.lat, validDevices[0].latestLocation!.lng] 
    : defaultCenter;

  const getDeviceIcon = (device: DeviceWithLatestLocation) => {
    if (device.latestLocation?.emergency_state) return sosIcon;
    
    // Consider device offline if not seen for 60 minutes
    const lastSeen = new Date(device.last_seen_at || 0).getTime();
    const isOffline = (now - lastSeen) > 60 * 60 * 1000;
    
    return isOffline ? offlineIcon : activeIcon;
  };

  const formatDate = (isoString: string) => {
    return new Date(isoString).toLocaleString('pt-PT');
  };

  return (
    <MapContainer 
      center={center} 
      zoom={validDevices.length > 0 ? 12 : 6} 
      style={{ height: '100%', width: '100%', borderRadius: '12px' }}
    >
      {/* Dark theme Map tiles (CartoDB Dark Matter) */}
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
        url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
      />
      
      {validDevices.map((device) => (
        <Marker 
          key={device.id} 
          position={[device.latestLocation!.lat, device.latestLocation!.lng]}
          icon={getDeviceIcon(device)}
        >
          <Popup className="custom-popup">
            <div style={{ padding: '4px' }}>
              <h3 style={{ margin: '0 0 8px 0', fontSize: '16px', fontWeight: 'bold' }}>
                {device.label}
              </h3>
              <p style={{ margin: '4px 0', fontSize: '14px' }}>
                <strong>Status:</strong> {device.latestLocation?.emergency_state ? '🚨 SOS' : '✅ Active'}
              </p>
              <p style={{ margin: '4px 0', fontSize: '14px' }}>
                <strong>Battery:</strong> {device.latestLocation?.battery_level}% {device.latestLocation?.battery_charging ? '⚡' : ''}
              </p>
              <p style={{ margin: '4px 0', fontSize: '14px' }}>
                <strong>Speed:</strong> {device.latestLocation?.speed.toFixed(1)} km/h
              </p>
              <p style={{ margin: '4px 0', fontSize: '14px', color: '#666' }}>
                <strong>Last seen:</strong> {device.last_seen_at ? formatDate(device.last_seen_at) : 'N/A'}
              </p>
            </div>
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  );
}
