import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { isValidSerialNumber } from '../../../types/telemetry';
import { timingSafeEqual } from 'crypto';

export interface LocationV2Item {
  timestamp: string;
  batteryLevel: number;
  batteryCharging: boolean;
  gps: {
    lat: number;
    lng: number;
    accuracy: number;
    speed: number;
    heading: number;
  };
  emergencyState: boolean;
  trackingEnabled: boolean;
  networkType: string;
  appVersion: string;
  markerColor?: string;
}

export interface LocationV2Payload {
  id: string;
  deviceLabel?: string;
  farmId?: string;
  locations: LocationV2Item[];
}

export async function POST(request: NextRequest) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ success: false, error: 'Invalid JSON body' }, { status: 400 });
  }

  const payload = body as LocationV2Payload;

  if (!payload || !payload.id || !isValidSerialNumber(payload.id)) {
    return NextResponse.json({ success: false, error: 'Invalid or missing device serial number (id)' }, { status: 400 });
  }

  if (!Array.isArray(payload.locations) || payload.locations.length === 0) {
    return NextResponse.json({ success: false, error: 'locations must be a non-empty array' }, { status: 400 });
  }

  const MAX_BATCH_SIZE = 100;
  if (payload.locations.length > MAX_BATCH_SIZE) {
    return NextResponse.json({ success: false, error: `Batch size exceeds maximum of ${MAX_BATCH_SIZE}` }, { status: 400 });
  }

  // Validate each location item
  for (let i = 0; i < payload.locations.length; i++) {
    const loc = payload.locations[i];
    if (!loc.timestamp || typeof loc.timestamp !== 'string') {
      return NextResponse.json({ success: false, error: `Invalid timestamp at index ${i}` }, { status: 400 });
    }
    if (typeof loc.batteryLevel !== 'number' || typeof loc.batteryCharging !== 'boolean') {
      return NextResponse.json({ success: false, error: `Invalid battery details at index ${i}` }, { status: 400 });
    }
    if (!loc.gps || typeof loc.gps.lat !== 'number' || typeof loc.gps.lng !== 'number' || typeof loc.gps.accuracy !== 'number') {
      return NextResponse.json({ success: false, error: `Invalid gps details at index ${i}` }, { status: 400 });
    }
    if (loc.gps.lat < -90 || loc.gps.lat > 90 || loc.gps.lng < -180 || loc.gps.lng > 180) {
      return NextResponse.json({ success: false, error: `Coordinates out of bounds at index ${i}` }, { status: 400 });
    }
    if (typeof loc.emergencyState !== 'boolean' || typeof loc.trackingEnabled !== 'boolean') {
      return NextResponse.json({ success: false, error: `Invalid tracking/emergency states at index ${i}` }, { status: 400 });
    }
  }

  // Find the latest location in the batch by timestamp to update the device status
  const sorted = [...payload.locations].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
  const latest = sorted[sorted.length - 1];

  const authHeader = request.headers.get('authorization') || '';
  const expected = `Bearer ${process.env.DEVICE_API_SECRET}`;
  const isValidSecret = authHeader.length === expected.length && timingSafeEqual(Buffer.from(authHeader), Buffer.from(expected));

  if (!isValidSecret) {
    return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
  }

  const supabase = getSupabaseAdmin();

  // 1. Upsert device using latest status
  const { error: deviceError } = await supabase
    .from('devices')
    .upsert(
      {
        id: payload.id,
        label: payload.deviceLabel || 'Dispositivo',
        last_seen_at: new Date().toISOString(),
        tracking_enabled: latest.trackingEnabled,
        app_version: latest.appVersion,
        ...(latest.markerColor ? { marker_color: latest.markerColor.toUpperCase() } : {}),
        ...(payload.farmId ? { farm_id: payload.farmId } : {})
      },
      {
        onConflict: 'id',
        ignoreDuplicates: false,
      }
    );

  if (deviceError) {
    console.error('[POST /api/v2/location] Device upsert error:', deviceError);
    return NextResponse.json(
      { success: false, error: 'Database error (device upsert)' },
      { status: 500 }
    );
  }

  // 2. Prepare bulk insert for locations
  const locationRows = payload.locations.map((loc) => ({
    device_id: payload.id,
    lat: loc.gps.lat,
    lng: loc.gps.lng,
    accuracy: loc.gps.accuracy,
    speed: loc.gps.speed,
    heading: loc.gps.heading,
    battery_level: loc.batteryLevel,
    battery_charging: loc.batteryCharging,
    emergency_state: loc.emergencyState,
    network_type: loc.networkType,
    tracking_enabled: loc.trackingEnabled,
    app_version: loc.appVersion,
    created_at: loc.timestamp,
  }));

  const { error: locationError } = await supabase
    .from('locations')
    .insert(locationRows);

  if (locationError) {
    console.error('[POST /api/v2/location] Locations insert error:', locationError);
    return NextResponse.json(
      { success: false, error: 'Database error (locations insert)' },
      { status: 500 }
    );
  }

  return NextResponse.json({
    success: true,
    message: `Batch of ${payload.locations.length} locations stored successfully`,
  });
}
