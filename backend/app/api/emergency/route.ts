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
import { timingSafeEqual } from 'crypto';
import { getAuthenticatedUser } from '../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../lib/supabase';
import { validateEmergencyPayload, ApiResponse } from '../../types/telemetry';
import { sendEmergencyNotifications } from '../../lib/notifications';
import { sendSosPushToAll } from '../../lib/fcm';


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

  if (!validateEmergencyPayload(body)) {
    return NextResponse.json(
      {
        success: false,
        error: 'Emergency payload validation failed',
        details: 'Required: serialNumber (hex string), deviceLabel, timestamp, batteryLevel, batteryCharging, gps {lat,lng,accuracy,speed,heading}, networkType, appVersion',
      },
      { status: 400 }
    );
  }

  const payload = body;
  const authHeader = request.headers.get('authorization') || '';
  const expected = `Bearer ${process.env.DEVICE_API_SECRET}`;
  const isDevice = authHeader.length === expected.length && timingSafeEqual(Buffer.from(authHeader), Buffer.from(expected));

  let supabase;
  let userId: string | null = null;

  if (isDevice) {
    supabase = getSupabaseAdmin();
  } else {
    supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: authError } = await getAuthenticatedUser(request, supabase);
    if (authError || !user) {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }
    userId = user.id;
  }

  // ── 2. Upsert device ──────────────────────────────────────────────────────
  const deviceUpdatePayload: {
    id: string;
    label: string;
    last_seen_at: string;
    app_version: string;
    user_id?: string;
  } = {
    id: payload.serialNumber,
    label: payload.deviceLabel,
    last_seen_at: new Date().toISOString(),
    app_version: payload.appVersion,
  };
  
  if (userId) {
    deviceUpdatePayload.user_id = userId;
  }

  const { error: deviceError } = await supabase
    .from('devices')
    .upsert(
      deviceUpdatePayload,
      { onConflict: 'id', ignoreDuplicates: false }
    );

  if (deviceError) {
    console.error('[POST /api/emergency] Device upsert error:', deviceError);
    return NextResponse.json(
      { success: false, error: 'Database error (device upsert)' },
      { status: 500 }
    );
  }

  const { error: locationError } = await supabase.from('locations').upsert({
    device_id: payload.serialNumber,
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
  }, { onConflict: 'device_id,created_at', ignoreDuplicates: true });

  if (locationError) {
    console.error('[POST /api/emergency] Location insert error:', locationError);
    return NextResponse.json(
      { success: false, error: 'Database error (SOS insert)' },
      { status: 500 }
    );
  }

  // ── 4. SOS deduplication — avoid notification spam ─────────────────────────
  // Check if we already sent notifications for this device in the last 60s.
  // If so, store the SOS location (done above) but skip re-triggering alerts.
  const { data: recentSos } = await supabase
    .from('locations')
    .select('id')
    .eq('device_id', payload.serialNumber)
    .eq('emergency_state', true)
    .gte('synced_at', new Date(Date.now() - 60_000).toISOString())
    .order('synced_at', { ascending: false })
    .limit(2);  // 2 because the one we just inserted will be included

  if (recentSos && recentSos.length > 1) {
    // SOS already active — location stored, notifications already sent
    return NextResponse.json(
      { success: true, message: 'SOS location updated (alerts already active)' },
      { status: 200 }
    );
  }

  // Log to server console for immediate visibility
  console.warn(
    `🚨 SOS ACTIVATED — Device: ${payload.deviceLabel} (${payload.serialNumber}) ` +
    `| Lat: ${payload.gps.lat}, Lng: ${payload.gps.lng} ` +
    `| Battery: ${payload.batteryLevel}% ` +
    `| Time: ${payload.timestamp}`
  );

  // Trigger FCM push (all other devices) + Email + WhatsApp — fire & forget
  try {
    await Promise.all([
      sendSosPushToAll({
        senderDeviceId: payload.serialNumber,
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

