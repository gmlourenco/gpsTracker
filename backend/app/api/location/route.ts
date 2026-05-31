/**
 * POST /api/location
 *
 * Ingests a telemetry payload from a tracker device.
 * Performs:
 *   1. Payload validation against TelemetryPayload schema
 *   2. Upsert the device record (create if first time, update last_seen_at)
 *   3. Insert the location row
 *
 * Returns: 200 on success, 400 on invalid payload, 500 on DB error.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../lib/supabase';
import {
  getTelemetryValidationErrors,
  validateTelemetryPayload,
  ApiResponse,
} from '../../types/telemetry';

// Accuracy threshold: locations worse than this are stored but flagged (via accuracy field)
const MAX_ACCEPTED_ACCURACY_METERS = 500;

export async function POST(request: NextRequest): Promise<NextResponse<ApiResponse>> {
  // ── 1. Parse & validate body ──────────────────────────────────────────────
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { success: false, error: 'Invalid JSON body' },
      { status: 400 }
    );
  }

  if (!validateTelemetryPayload(body)) {
    const validationErrors = getTelemetryValidationErrors(body);
    return NextResponse.json(
      {
        success: false,
        error: 'Payload validation failed',
        details: validationErrors.join('; '),
      },
      { status: 400 }
    );
  }

  const payload = body;
  const supabase = getSupabaseAdmin();

  // ── 2. Upsert device ──────────────────────────────────────────────────────
  const { error: deviceError } = await supabase
    .from('devices')
    .upsert(
      {
        id: payload.serialNumber,
        label: payload.deviceLabel,
        marker_color: payload.markerColor?.toUpperCase() ?? '#16A34A',
        last_seen_at: new Date().toISOString(),
        tracking_enabled: payload.trackingEnabled,
        app_version: payload.appVersion,
      },
      {
        onConflict: 'id',
        ignoreDuplicates: false, // always update last_seen_at
      }
    );

  if (deviceError) {
    console.error('[POST /api/location] Device upsert error:', deviceError);
    return NextResponse.json(
      { success: false, error: 'Database error (device upsert)', details: deviceError.message },
      { status: 500 }
    );
  }

  // ── 3. Insert location ────────────────────────────────────────────────────
  // Clamp accuracy — very poor fixes are still stored for audit trail
  const isLowAccuracy = payload.gps.accuracy > MAX_ACCEPTED_ACCURACY_METERS;

  const { error: locationError } = await supabase.from('locations').insert({
    device_id: payload.serialNumber,
    lat: payload.gps.lat,
    lng: payload.gps.lng,
    accuracy: payload.gps.accuracy,
    speed: payload.gps.speed,
    heading: payload.gps.heading,
    battery_level: payload.batteryLevel,
    battery_charging: payload.batteryCharging,
    emergency_state: payload.emergencyState,
    network_type: payload.networkType,
    tracking_enabled: payload.trackingEnabled,
    app_version: payload.appVersion,
    created_at: payload.timestamp,
    // synced_at defaults to NOW() in the DB
  });

  if (locationError) {
    console.error('[POST /api/location] Location insert error:', locationError);
    return NextResponse.json(
      { success: false, error: 'Database error (location insert)', details: locationError.message },
      { status: 500 }
    );
  }

  return NextResponse.json(
    {
      success: true,
      message: isLowAccuracy
        ? 'Location stored (low accuracy flagged)'
        : 'Location stored successfully',
    },
    { status: 200 }
  );
}
