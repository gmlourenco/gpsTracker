/**
 * GET /api/positions/last
 *
 * Returns every device with its most recent known position (alias for dashboard / map clients).
 * Same data as GET /api/devices but wrapped with metadata for mobile consumers.
 */

import { NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import {
  DeviceRecord,
  DeviceWithLatestLocation,
  LocationRecord,
} from '../../../types/telemetry';

export async function GET(): Promise<NextResponse> {
  const supabase = getSupabaseAdmin();

  const { data: devices, error: devicesError } = await supabase
    .from('devices')
    .select('*')
    .order('last_seen_at', { ascending: false, nullsFirst: false });

  if (devicesError) {
    console.error('[GET /api/positions/last] Devices error:', devicesError);
    return NextResponse.json(
      { success: false, error: 'Database error', details: devicesError.message },
      { status: 500 }
    );
  }

  if (!devices?.length) {
    return NextResponse.json({
      success: true,
      fetchedAt: new Date().toISOString(),
      devices: [] as DeviceWithLatestLocation[],
    });
  }

  const deviceIds = (devices as DeviceRecord[]).map((d) => d.id);

  const { data: latestLocations, error: locationsError } = await supabase
    .from('locations')
    .select('*')
    .in('device_id', deviceIds)
    .order('synced_at', { ascending: false });

  if (locationsError) {
    console.error('[GET /api/positions/last] Locations error:', locationsError);
    return NextResponse.json(
      { success: false, error: 'Database error (locations)', details: locationsError.message },
      { status: 500 }
    );
  }

  const latestByDevice = new Map<string, LocationRecord>();
  for (const loc of (latestLocations ?? []) as LocationRecord[]) {
    if (!latestByDevice.has(loc.device_id)) {
      latestByDevice.set(loc.device_id, loc);
    }
  }

  const result: DeviceWithLatestLocation[] = (devices as DeviceRecord[]).map((device) => ({
    ...device,
    latestLocation: latestByDevice.get(device.id) ?? null,
  }));

  return NextResponse.json({
    success: true,
    fetchedAt: new Date().toISOString(),
    devices: result,
  });
}
