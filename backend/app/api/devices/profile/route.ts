/**
 * PATCH /api/devices/profile
 *
 * Updates device identity fields (label, marker color) without requiring a GPS fix.
 * Called from the mobile Config screen on save.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { isValidMarkerColor } from '../../../lib/app-version';
import { ApiResponse, isValidUuid } from '../../../types/telemetry';

interface DeviceProfileBody {
  deviceId: string;
  deviceLabel: string;
  markerColor: string;
}

function validateProfileBody(body: unknown): body is DeviceProfileBody {
  if (typeof body !== 'object' || body === null) return false;
  const p = body as Record<string, unknown>;
  return (
    isValidUuid(p.deviceId) &&
    typeof p.deviceLabel === 'string' &&
    p.deviceLabel.trim() !== '' &&
    isValidMarkerColor(p.markerColor)
  );
}

export async function PATCH(request: NextRequest): Promise<NextResponse<ApiResponse>> {
  const authHeader = request.headers.get('authorization');
  const deviceSecret = process.env.DEVICE_API_SECRET;

  if (deviceSecret && authHeader !== `Bearer ${deviceSecret}`) {
    return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ success: false, error: 'Invalid JSON body' }, { status: 400 });
  }

  if (!validateProfileBody(body)) {
    return NextResponse.json(
      {
        success: false,
        error: 'Validation failed',
        details: 'Required: deviceId (UUID), deviceLabel (non-empty), markerColor (#RRGGBB)',
      },
      { status: 400 }
    );
  }

  const supabase = getSupabaseAdmin();

  const { error } = await supabase.from('devices').upsert(
    {
      id: body.deviceId,
      label: body.deviceLabel.trim(),
      marker_color: body.markerColor.toUpperCase(),
    },
    { onConflict: 'id', ignoreDuplicates: false }
  );

  if (error) {
    console.error('[PATCH /api/devices/profile] Upsert error:', error);
    return NextResponse.json(
      { success: false, error: 'Database error', details: error.message },
      { status: 500 }
    );
  }

  return NextResponse.json({ success: true, message: 'Device profile updated' });
}
