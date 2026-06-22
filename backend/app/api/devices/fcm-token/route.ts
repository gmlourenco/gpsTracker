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

import { timingSafeEqual } from 'crypto';

export async function PATCH(request: NextRequest) {
  const authHeader = request.headers.get('authorization') || '';
  const expected = `Bearer ${process.env.DEVICE_API_SECRET}`;
  
  if (authHeader.length !== expected.length || !timingSafeEqual(Buffer.from(authHeader), Buffer.from(expected))) {
    return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
  }

  // ── 2. Parse body ─────────────────────────────────────────────────────────
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ success: false, error: 'Invalid JSON body' }, { status: 400 });
  }

  const { serialNumber, fcmToken } = body as { serialNumber?: string; fcmToken?: string };

  if (!serialNumber || !fcmToken || typeof serialNumber !== 'string' || typeof fcmToken !== 'string') {
    return NextResponse.json(
      { success: false, error: 'Required: serialNumber (string), fcmToken (string)' },
      { status: 400 }
    );
  }

  // ── 3. Update device record ───────────────────────────────────────────────
  const supabase = getSupabaseAdmin();

  const { data, error } = await supabase
    .from('devices')
    .update({ fcm_token: fcmToken, last_seen_at: new Date().toISOString() })
    .eq('id', serialNumber)
    .select('id');

  if (error) {
    console.error('[PATCH /api/devices/fcm-token] DB error:', error.message);
    return NextResponse.json(
      { success: false, error: 'Database error' },
      { status: 500 }
    );
  }

  // If no rows were updated, the device doesn't exist yet. Create it.
  if (!data || data.length === 0) {
    const { error: insertError } = await supabase
      .from('devices')
      .insert({
        id: serialNumber,
        fcm_token: fcmToken,
        label: 'Dispositivo', // Default label for new devices
        last_seen_at: new Date().toISOString(),
      });

    if (insertError) {
      console.error('[PATCH /api/devices/fcm-token] DB insert error:', insertError.message);
      return NextResponse.json(
        { success: false, error: 'Database insert error' },
        { status: 500 }
      );
    }
    console.log(`[PATCH /api/devices/fcm-token] New device registered with token: ${serialNumber}`);
  } else {
    console.log(`[PATCH /api/devices/fcm-token] Token updated for device: ${serialNumber}`);
  }

  return NextResponse.json({ success: true });
}
