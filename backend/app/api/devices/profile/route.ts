/**
 * PATCH /api/devices/profile
 *
 * Updates device identity fields (label, marker color) without requiring a GPS fix.
 * Called from the mobile Config screen on save.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { isValidMarkerColor } from '../../../lib/app-version';
import { ApiResponse, isValidSerialNumber } from '../../../types/telemetry';

interface DeviceProfileBody {
  serialNumber: string;
  deviceLabel: string;
  markerColor: string;
}

function validateProfileBody(body: unknown): body is DeviceProfileBody {
  if (typeof body !== 'object' || body === null) return false;
  const p = body as Record<string, unknown>;
  return (
    isValidSerialNumber(p.serialNumber) &&
    typeof p.deviceLabel === 'string' &&
    p.deviceLabel.trim() !== '' &&
    isValidMarkerColor(p.markerColor)
  );
}

export async function PATCH(request: NextRequest): Promise<NextResponse<ApiResponse>> {

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
        details: 'Required: serialNumber (string), deviceLabel (non-empty), markerColor (#RRGGBB)',
      },
      { status: 400 }
    );
  }

  const supabase = getSupabaseAdmin();

  const { error } = await supabase.from('devices').upsert(
    {
      id: body.serialNumber,
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
