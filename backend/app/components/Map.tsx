'use client';

import React, { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { DeviceWithLatestLocation } from '../types/telemetry';
import { getGoogleMapsDirectionsUrl } from '../lib/navigation';

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
  const [mapTheme, setMapTheme] = useState<'dark' | 'light' | 'satellite'>('dark');
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
        
        /* Dark Theme Leaflet Popup Styling */
        .custom-popup .leaflet-popup-content-wrapper {
          background: #16213E !important;
          color: #FFFFFF !important;
          border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
          border-radius: 12px !important;
          box-shadow: 0 4px 12px rgba(0,0,0,0.5) !important;
        }
        .custom-popup .leaflet-popup-content {
          margin: 0 !important;
          background: #16213E !important;
          border-radius: 12px !important;
        }
        .custom-popup .leaflet-popup-tip {
          background: #16213E !important;
          border: 1px solid rgba(255, 255, 255, 0.08) !important;
        }
        .custom-popup .leaflet-popup-close-button {
          color: #94A3B8 !important;
          padding: 6px !important;
          margin-top: 4px !important;
          margin-right: 4px !important;
        }

        /* Map Theme Switcher Control */
        .theme-switcher-container {
          position: absolute;
          top: 10px;
          left: 60px;
          z-index: 1000;
          background: rgba(22, 33, 62, 0.9) !important;
          backdrop-filter: blur(8px);
          border: 1.5px solid rgba(255, 255, 255, 0.08);
          border-radius: 10px;
          display: flex;
          padding: 3px;
          gap: 3px;
          box-shadow: 0 4px 12px rgba(0,0,0,0.4);
        }
        .theme-btn {
          background: transparent;
          color: #94A3B8;
          border: none;
          border-radius: 7px;
          padding: 6px 12px;
          font-size: 12px;
          font-weight: 600;
          font-family: system-ui, -apple-system, sans-serif;
          cursor: pointer;
          transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
          display: flex;
          align-items: center;
          gap: 4px;
        }
        .theme-btn:hover {
          color: #FFFFFF;
          background: rgba(255, 255, 255, 0.04);
        }
        .theme-btn.active {
          background: #3B82F6 !important;
          color: #FFFFFF !important;
          box-shadow: 0 2px 4px rgba(59, 130, 246, 0.3);
        }
      `}} />
      
      {/* Map Theme Selector Overlay */}
      <div className="theme-switcher-container">
        <button 
          type="button"
          onClick={() => setMapTheme('dark')}
          className={`theme-btn ${mapTheme === 'dark' ? 'active' : ''}`}
        >
          🌙 Escuro
        </button>
        <button 
          type="button"
          onClick={() => setMapTheme('light')}
          className={`theme-btn ${mapTheme === 'light' ? 'active' : ''}`}
        >
          ☀️ Claro
        </button>
        <button 
          type="button"
          onClick={() => setMapTheme('satellite')}
          className={`theme-btn ${mapTheme === 'satellite' ? 'active' : ''}`}
        >
          🛰️ Satélite
        </button>
      </div>

      <MapContainer 
        center={center} 
        zoom={validDevices.length > 0 ? 12 : 6} 
        style={{ height: '100%', width: '100%', borderRadius: '12px' }}
        attributionControl={false}
      >
        {/* Fit Map view bounds dynamically if we have multiple devices */}
        {validDevices.length > 0 && <ChangeMapView bounds={bounds} />}
        
        {/* Dynamic theme Map tiles */}
        {mapTheme === 'dark' && (
          <TileLayer
            key="dark-theme"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          />
        )}
        {mapTheme === 'light' && (
          <TileLayer
            key="light-theme"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
          />
        )}
        {mapTheme === 'satellite' && (
          <TileLayer
            key="satellite-theme"
            attribution='&copy; Google Maps contributors'
            url="https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}&scale=2"
          />
        )}
        
        {validDevices.map((device) => {
          const isSos = !!device.latestLocation?.emergency_state;
          const parsedColor = device.marker_color || '#16A34A';
          return (
            <Marker 
              key={device.id} 
              position={[device.latestLocation!.lat, device.latestLocation!.lng]}
              icon={getDeviceIcon(device)}
            >
              <Popup className="custom-popup">
                <div style={{ 
                  padding: '16px', 
                  minWidth: '230px', 
                  fontFamily: 'system-ui, -apple-system, sans-serif'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                    <div style={{
                      backgroundColor: parsedColor,
                      width: '28px',
                      height: '28px',
                      borderRadius: '50%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 'bold',
                      fontSize: '13px',
                      color: '#FFFFFF',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.3)'
                    }}>
                      {device.label.trim().charAt(0).toUpperCase() || '?'}
                    </div>
                    <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 'bold', color: '#FFFFFF' }}>
                      {device.label}
                    </h3>
                  </div>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '13px', color: '#94A3B8', marginBottom: '14px' }}>
                    <p style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <strong>Status:</strong>
                      {isSos ? (
                        <span style={{ color: '#F87171', fontWeight: 'bold' }}>🚨 SOS ATIVO</span>
                      ) : (
                        <span style={{ color: '#4ADE80' }}>Ativo</span>
                      )}
                    </p>
                    <p style={{ margin: 0 }}>
                      🔋 {device.latestLocation?.battery_level}% {device.latestLocation?.battery_charging ? '⚡' : ''}
                    </p>
                    <p style={{ margin: 0 }}>
                      📍 {device.latestLocation?.speed.toFixed(1)} km/h
                    </p>
                    <p style={{ margin: 0, fontSize: '11px', opacity: 0.7 }}>
                      📱 v{device.latestLocation?.app_version || device.app_version || '--'}
                    </p>
                    <p style={{ margin: 0, fontSize: '11px', opacity: 0.5 }}>
                      Último sinal: {device.last_seen_at ? formatDate(device.last_seen_at) : 'N/A'}
                    </p>
                  </div>
                  
                  <a 
                    href={getGoogleMapsDirectionsUrl(device.latestLocation!.lat, device.latestLocation!.lng)}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      display: 'block',
                      textAlign: 'center',
                      padding: '8px 12px',
                      backgroundColor: isSos ? '#DC2626' : '#3B82F6',
                      color: '#FFFFFF',
                      borderRadius: '6px',
                      textDecoration: 'none',
                      fontWeight: 'bold',
                      fontSize: '12px',
                      transition: 'opacity 0.2s ease-in-out'
                    }}
                  >
                    ➔ Iniciar Navegação
                  </a>
                </div>
              </Popup>
            </Marker>
          );
        })}
      </MapContainer>
    </div>
  );
}
