/**
 * PATCH /api/devices/fcm-token
 *
 * Stores/updates the FCM push token for a device.
 * Called by the Android app on every launch and whenever FCM rotates the token.
 *
 * Body: { deviceId: string, fcmToken: string }
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';

export async function PATCH(request: NextRequest) {
  // ── 1. Authorization ──────────────────────────────────────────────────────
  const authHeader = request.headers.get('authorization');
  const deviceSecret = process.env.DEVICE_API_SECRET;

  if (deviceSecret && authHeader !== `Bearer ${deviceSecret}`) {
    return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
  }

  // ── 2. Parse body ─────────────────────────────────────────────────────────
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ success: false, error: 'Invalid JSON body' }, { status: 400 });
  }

  const { deviceId, fcmToken } = body as { deviceId?: string; fcmToken?: string };

  if (!deviceId || !fcmToken || typeof deviceId !== 'string' || typeof fcmToken !== 'string') {
    return NextResponse.json(
      { success: false, error: 'Required: deviceId (string), fcmToken (string)' },
      { status: 400 }
    );
  }

  // ── 3. Update device record ───────────────────────────────────────────────
  const supabase = getSupabaseAdmin();

  const { error } = await supabase
    .from('devices')
    .update({ fcm_token: fcmToken, last_seen_at: new Date().toISOString() })
    .eq('id', deviceId);

  if (error) {
    console.error('[PATCH /api/devices/fcm-token] DB error:', error.message);
    return NextResponse.json(
      { success: false, error: 'Database error', details: error.message },
      { status: 500 }
    );
  }

  console.log(`[PATCH /api/devices/fcm-token] Token updated for device ${deviceId}`);
  return NextResponse.json({ success: true });
}
