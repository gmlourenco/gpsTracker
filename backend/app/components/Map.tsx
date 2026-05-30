'use client';

import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
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

// Helper to create custom HTML markers matching the app's visual identity
const createCustomIcon = (label: string, color: string, isEmergency: boolean) => {
  const initial = label.trim().charAt(0).toUpperCase() || '?';
  const pulseClass = isEmergency ? 'sos-pulse' : '';
  
  const html = `
    <div class="custom-map-marker ${pulseClass}" style="
      background-color: ${color || '#16A34A'};
      width: 32px;
      height: 32px;
      border-radius: 50%;
      border: 2.5px solid white;
      color: white;
      font-weight: bold;
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 5px rgba(0,0,0,0.4);
      transition: all 0.3s ease;
    ">
      ${initial}
    </div>
  `;
  
  return L.divIcon({
    html: html,
    className: 'custom-leaflet-icon',
    iconSize: [32, 32],
    iconAnchor: [16, 16],
    popupAnchor: [0, -16]
  });
};

// Component to dynamically fit map bounds to show all markers
function ChangeMapView({ bounds }: { bounds: L.LatLngBounds }) {
  const map = useMap();
  useEffect(() => {
    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 14 });
    }
  }, [bounds, map]);
  return null;
}

interface MapProps {
  devices: DeviceWithLatestLocation[];
}

export default function Map({ devices }: MapProps) {
  const defaultCenter: [number, number] = [39.3999, -8.2245];
  // eslint-disable-next-line react-hooks/purity
  const now = Date.now(); // Cache the current time to avoid calling impure function repeatedly
  
  const validDevices = devices.filter(d => d.latestLocation && d.latestLocation.lat !== undefined && d.latestLocation.lng !== undefined);
  
  const center: [number, number] = validDevices.length > 0 
    ? [validDevices[0].latestLocation!.lat, validDevices[0].latestLocation!.lng] 
    : defaultCenter;

  const bounds = L.latLngBounds(
    validDevices.map(d => [d.latestLocation!.lat, d.latestLocation!.lng])
  );

  const getDeviceIcon = (device: DeviceWithLatestLocation) => {
    const isEmergency = !!device.latestLocation?.emergency_state;
    const color = device.marker_color;
    return createCustomIcon(device.label, color, isEmergency);
  };

  const formatDate = (isoString: string) => {
    return new Date(isoString).toLocaleString('pt-PT');
  };

  return (
    <div style={{ height: '100%', width: '100%', position: 'relative' }}>
      <style dangerouslySetInnerHTML={{ __html: `
        @keyframes sos-pulse-anim {
          0% {
            transform: scale(1);
            box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.8), 0 2px 5px rgba(0,0,0,0.4);
          }
          70% {
            transform: scale(1.15);
            box-shadow: 0 0 0 12px rgba(220, 38, 38, 0), 0 2px 5px rgba(0,0,0,0.4);
          }
          100% {
            transform: scale(1);
            box-shadow: 0 0 0 0 rgba(220, 38, 38, 0), 0 2px 5px rgba(0,0,0,0.4);
          }
        }
        .sos-pulse {
          animation: sos-pulse-anim 1.5s infinite ease-in-out;
          border-color: #FFFFFF !important;
          background-color: #DC2626 !important;
        }
        .custom-leaflet-icon {
          background: transparent;
          border: none;
        }
      `}} />
      <MapContainer 
        center={center} 
        zoom={validDevices.length > 0 ? 12 : 6} 
        style={{ height: '100%', width: '100%', borderRadius: '12px' }}
      >
        {/* Fit Map view bounds dynamically if we have multiple devices */}
        {validDevices.length > 0 && <ChangeMapView bounds={bounds} />}
        
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
                <h3 style={{ margin: '0 0 8px 0', fontSize: '16px', fontWeight: 'bold', color: device.marker_color || '#16A34A' }}>
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
    </div>
  );
}
