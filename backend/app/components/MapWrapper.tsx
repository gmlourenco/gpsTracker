'use client';

import dynamic from 'next/dynamic';
import React from 'react';
import { DeviceWithLatestLocation } from '../types/telemetry';
// Ignore the Map props typing issue easily by passing any for dynamic wrapper
const MapWithNoSSR = dynamic(() => import('./Map'), {
  ssr: false,
  loading: () => <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#888' }}>Loading map...</div>
});

export default function MapWrapper({ devices }: { devices: DeviceWithLatestLocation[] }) {
  return <MapWithNoSSR devices={devices} />;
}
