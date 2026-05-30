/**
 * POST /api/emergency
 *
 * High-priority SOS endpoint. Designed to be called immediately when a
 * device activates the emergency button. Bypasses any batching or queuing.
 *
 * Behaviour:
 *   1. Authorization check
 *   2. Validate EmergencyPayload
 *   3. Upsert device with last_seen_at
 *   4. Insert location row with emergency_state = TRUE (forced, regardless of payload)
 *
 * This endpoint is intentionally separate from /api/location so it can be
 * given higher priority in Vercel (dedicated function, no cold-start delay),
 * and to make it trivial to add push notification hooks here in the future.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../lib/supabase';
import { validateEmergencyPayload, ApiResponse } from '../../types/telemetry';
import { sendEmergencyNotifications } from '../../lib/notifications';
import { sendSosPushToAll } from '../../lib/fcm';


export async function POST(request: NextRequest): Promise<NextResponse<ApiResponse>> {
  // ── 1. Authorization ──────────────────────────────────────────────────────
  const authHeader = request.headers.get('authorization');
  const deviceSecret = process.env.DEVICE_API_SECRET;

  if (deviceSecret && authHeader !== `Bearer ${deviceSecret}`) {
    return NextResponse.json(
      { success: false, error: 'Unauthorized' },
      { status: 401 }
    );
  }

  // ── 2. Parse & validate body ──────────────────────────────────────────────
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { success: false, error: 'Invalid JSON body' },
      { status: 400 }
    );
  }

  if (!validateEmergencyPayload(body)) {
    return NextResponse.json(
      {
        success: false,
        error: 'Emergency payload validation failed',
        details: 'Required: deviceId (UUID), deviceLabel, timestamp, batteryLevel, batteryCharging, gps {lat,lng,accuracy,speed,heading}, networkType, appVersion',
      },
      { status: 400 }
    );
  }

  const payload = body;
  const supabase = getSupabaseAdmin();

  // ── 3. Upsert device ──────────────────────────────────────────────────────
  const { error: deviceError } = await supabase
    .from('devices')
    .upsert(
      {
        id: payload.deviceId,
        label: payload.deviceLabel,
        last_seen_at: new Date().toISOString(),
        app_version: payload.appVersion,
      },
      { onConflict: 'id', ignoreDuplicates: false }
    );

  if (deviceError) {
    console.error('[POST /api/emergency] Device upsert error:', deviceError);
    return NextResponse.json(
      { success: false, error: 'Database error (device upsert)', details: deviceError.message },
      { status: 500 }
    );
  }

  // ── 4. Insert SOS location — emergency_state is ALWAYS true here ──────────
  const { error: locationError } = await supabase.from('locations').insert({
    device_id: payload.deviceId,
    lat: payload.gps.lat,
    lng: payload.gps.lng,
    accuracy: payload.gps.accuracy,
    speed: payload.gps.speed,
    heading: payload.gps.heading,
    battery_level: payload.batteryLevel,
    battery_charging: payload.batteryCharging,
    emergency_state: true,           // Always forced TRUE on this endpoint
    network_type: payload.networkType,
    tracking_enabled: true,
    app_version: payload.appVersion,
    created_at: payload.timestamp,
  });

  if (locationError) {
    console.error('[POST /api/emergency] Location insert error:', locationError);
    return NextResponse.json(
      { success: false, error: 'Database error (SOS insert)', details: locationError.message },
      { status: 500 }
    );
  }

  // Log to server console for immediate visibility
  console.warn(
    `🚨 SOS ACTIVATED — Device: ${payload.deviceLabel} (${payload.deviceId}) ` +
    `| Lat: ${payload.gps.lat}, Lng: ${payload.gps.lng} ` +
    `| Battery: ${payload.batteryLevel}% ` +
    `| Time: ${payload.timestamp}`
  );

  // Trigger FCM push (all other devices) + Email + WhatsApp — fire & forget
  try {
    await Promise.all([
      sendSosPushToAll({
        senderDeviceId: payload.deviceId,
        deviceLabel:    payload.deviceLabel,
        lat:            payload.gps.lat,
        lng:            payload.gps.lng,
        batteryLevel:   payload.batteryLevel,
        timestamp:      payload.timestamp,
      }),
      sendEmergencyNotifications({
        deviceLabel:  payload.deviceLabel,
        lat:          payload.gps.lat,
        lng:          payload.gps.lng,
        batteryLevel: payload.batteryLevel,
        timestamp:    payload.timestamp,
      }),
    ]);
  } catch (err) {
    console.error('[POST /api/emergency] Alert dispatch failed:', err);
  }

  return NextResponse.json(
    { success: true, message: 'SOS recorded. Emergency state is ACTIVE.' },
    { status: 200 }
  );
}

