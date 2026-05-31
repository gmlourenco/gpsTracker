/**
 * GET /api/devices
 *
 * Returns all registered family devices with their latest known location.
 * Used by the web dashboard to render the device list and map markers.
 *
 * Response shape: DeviceWithLatestLocation[]
 *
 * A device with no location history will have latestLocation = null.
 * Devices are ordered by last_seen_at DESC (most recently active first).
 *
 * A device not seen for > 60 minutes has its status implicitly treated as
 * "Disconnected / Inactivity Alert" — the client is responsible for this
 * derived state based on the last_seen_at field.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../lib/supabase';
import { DeviceRecord, LocationRecord, DeviceWithLatestLocation } from '../../types/telemetry';

export async function GET(request: NextRequest): Promise<NextResponse> {
  const supabase = getSupabaseAdmin();

  // ── 1. Fetch all devices ──────────────────────────────────────────────────
  const { data: devices, error: devicesError } = await supabase
    .from('devices')
    .select('*')
    .order('last_seen_at', { ascending: false, nullsFirst: false });

  if (devicesError) {
    console.error('[GET /api/devices] Devices fetch error:', devicesError);
    return NextResponse.json(
      { success: false, error: 'Database error', details: devicesError.message },
      { status: 500 }
    );
  }

  if (!devices || devices.length === 0) {
    return NextResponse.json([] as DeviceWithLatestLocation[], { status: 200 });
  }

  // ── 2. Fetch the latest location for each device ──────────────────────────
  // Use a single query with DISTINCT ON equivalent:
  // For each device_id, get the row with the highest synced_at.
  const deviceIds = (devices as DeviceRecord[]).map((d) => d.id);

  const { data: latestLocations, error: locationsError } = await supabase
    .from('locations')
    .select('*')
    .in('device_id', deviceIds)
    .order('synced_at', { ascending: false });

  if (locationsError) {
    console.error('[GET /api/devices] Locations fetch error:', locationsError);
    return NextResponse.json(
      { success: false, error: 'Database error (locations)', details: locationsError.message },
      { status: 500 }
    );
  }

  // Build a map: deviceId → most recent location (first occurrence per device_id
  // since results are already ordered by synced_at DESC)
  const latestByDevice = new Map<string, LocationRecord>();
  for (const loc of (latestLocations ?? []) as LocationRecord[]) {
    if (!latestByDevice.has(loc.device_id)) {
      latestByDevice.set(loc.device_id, loc);
    }
  }

  // ── 3. Merge and return ───────────────────────────────────────────────────
  const result: DeviceWithLatestLocation[] = (devices as DeviceRecord[]).map((device) => ({
    ...device,
    latestLocation: latestByDevice.get(device.id) ?? null,
  }));

  return NextResponse.json(result, { status: 200 });
}
