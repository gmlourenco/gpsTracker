/**
 * GET & POST /api/devices/config
 *
 * GET: Retrieves saved configurations for a given device serialNumber.
 * POST: Saves or updates configuration parameters for a device.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';
import { timingSafeEqual } from 'crypto';
import { isValidSerialNumber } from '../../../types/telemetry';

const VALID_MAP_TYPES = ['SATELLITE', 'DARK', 'LIGHT'] as const;
const VALID_SENSOR_SENSITIVITIES = ['low', 'medium', 'high', 'off'] as const;

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const serialNumber = searchParams.get('serialNumber');

  if (!serialNumber || !isValidSerialNumber(serialNumber)) {
    return NextResponse.json(
      { success: false, error: 'Invalid or missing serialNumber parameter' },
      { status: 400 }
    );
  }

  const authHeader = request.headers.get('authorization') || '';
  const expected = `Bearer ${process.env.DEVICE_API_SECRET}`;
  const isDevice = authHeader.length === expected.length && timingSafeEqual(Buffer.from(authHeader), Buffer.from(expected));

  let supabase;
  let user = null;
  
  if (isDevice) {
    supabase = getSupabaseAdmin();
  } else {
    supabase = await getSupabaseServerClient(request);
    const { data: authData, error: authError } = await getAuthenticatedUser(request, supabase);
    if (authError || !authData.user) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }
    user = authData.user;
  }

  // Use admin client to query devices regardless, because if a device is protected, 
  // RLS might block reading it with a regular user client if policies are strict.
  // Actually, we want to fetch the device row to check its user_id.
  const adminSupabase = getSupabaseAdmin();
  const { data: device, error } = await adminSupabase
    .from('devices')
    .select('*')
    .eq('id', serialNumber)
    .maybeSingle();

  if (error) {
    console.error('[GET /api/devices/config] Database error:', error);
    return NextResponse.json(
      { success: false, error: 'Database error' },
      { status: 500 }
    );
  }

  if (device) {
    // DEVICE EXISTS
    if (device.user_id) {
      // Protected by an account
      if (user && user.id === device.user_id) {
        // Match
        return NextResponse.json({ success: true, config: mapDeviceToConfig(device) });
      } else {
        // Forbidden: device belongs to someone else (or request is anonymous/isDevice)
        return NextResponse.json({ success: false, error: 'Forbidden. Device is linked to another account.' }, { status: 403 });
      }
    } else {
      // Not protected by an account
      if (user) {
        // Auto-associate
        await adminSupabase.from('devices').update({ user_id: user.id }).eq('id', serialNumber);
        return NextResponse.json({ success: true, config: mapDeviceToConfig(device) });
      } else {
        // Legacy fallback
        return NextResponse.json({ success: true, config: mapDeviceToConfig(device) });
      }
    }
  } else {
    // DEVICE DOES NOT EXIST
    if (user) {
      // Find other devices for this user
      const { data: otherDevices } = await adminSupabase
        .from('devices')
        .select('id, label')
        .eq('user_id', user.id);
        
      if (otherDevices && otherDevices.length > 0) {
        return NextResponse.json({
          success: true,
          promptImport: true,
          availableDevices: otherDevices.map(d => ({ serial: d.id, name: d.label }))
        });
      } else {
        // New device, no other devices to import from
        return NextResponse.json({ success: true, config: null });
      }
    } else {
      // Anonymous new device
      return NextResponse.json({ success: true, config: null });
    }
  }
}

function mapDeviceToConfig(data: any) {
  return {
    serialNumber: data.id,
    deviceLabel: data.label || 'Dispositivo',
    markerColor: data.marker_color,
    trackingIntervalMs: data.tracking_interval_ms !== null ? Number(data.tracking_interval_ms) : 60000,
    trackingDistanceM: data.tracking_distance_m !== null ? Number(data.tracking_distance_m) : 200,
    defaultMapType: data.default_map_type || 'SATELLITE',
    accidentSensorSensitivity: data.accident_sensor_sensitivity || 'medium',
    configUpdatedAt: data.config_updated_at !== null ? Number(data.config_updated_at) : -1,
  };
}

interface ConfigItem {
  configName: string;
  configValue: unknown;
}

const CONFIG_COLUMN_MAP: Record<string, string> = {
  serialNumber: 'id',
  deviceLabel: 'label',
  markerColor: 'marker_color',
  trackingIntervalMs: 'tracking_interval_ms',
  trackingDistanceM: 'tracking_distance_m',
  defaultMapType: 'default_map_type',
  accidentSensorSensitivity: 'accident_sensor_sensitivity',
  configUpdatedAt: 'config_updated_at',
};

export async function POST(request: NextRequest) {
  let body: ConfigItem[];
  try {
    body = await request.json() as ConfigItem[];
  } catch {
    return NextResponse.json({ success: false, error: 'Invalid JSON body' }, { status: 400 });
  }

  if (!Array.isArray(body)) {
    return NextResponse.json({ success: false, error: 'Expected array of config items' }, { status: 400 });
  }

  const updatePayload: Record<string, unknown> = {};

  for (const item of body) {
    if (!item || typeof item.configName !== 'string') continue;
    const columnName = CONFIG_COLUMN_MAP[item.configName];
    if (columnName) {
      let val = item.configValue;
      if (columnName === 'tracking_interval_ms' || columnName === 'config_updated_at') {
        val = val !== null && val !== undefined ? Number(val) : null;
      } else if (columnName === 'tracking_distance_m') {
        val = val !== null && val !== undefined ? Number(val) : null;
      } else if (columnName === 'marker_color' && typeof val === 'string') {
        val = val.toUpperCase();
      } else if (columnName === 'default_map_type' && typeof val === 'string') {
        val = val.toUpperCase();
      } else if (columnName === 'accident_sensor_sensitivity' && typeof val === 'string') {
        val = val.toLowerCase();
      }
      updatePayload[columnName] = val;
    }
  }

  const serialNumber = updatePayload.id;
  if (!serialNumber || !isValidSerialNumber(serialNumber)) {
    return NextResponse.json({ success: false, error: 'Invalid or missing serialNumber configName' }, { status: 400 });
  }

  if (updatePayload.label !== undefined && (typeof updatePayload.label !== 'string' || updatePayload.label.trim() === '')) {
    return NextResponse.json({ success: false, error: 'Invalid deviceLabel' }, { status: 400 });
  }
  if (updatePayload.marker_color !== undefined && (typeof updatePayload.marker_color !== 'string' || !/^#[0-9A-Fa-f]{6}$/.test(updatePayload.marker_color))) {
    return NextResponse.json({ success: false, error: 'Invalid markerColor' }, { status: 400 });
  }
  if (updatePayload.tracking_interval_ms !== undefined && typeof updatePayload.tracking_interval_ms !== 'number') {
    return NextResponse.json({ success: false, error: 'Invalid trackingIntervalMs' }, { status: 400 });
  }
  if (updatePayload.tracking_distance_m !== undefined && typeof updatePayload.tracking_distance_m !== 'number') {
    return NextResponse.json({ success: false, error: 'Invalid trackingDistanceM' }, { status: 400 });
  }
  if (
    updatePayload.default_map_type !== undefined &&
    (typeof updatePayload.default_map_type !== 'string' ||
      !(VALID_MAP_TYPES as readonly string[]).includes(updatePayload.default_map_type as string))
  ) {
    return NextResponse.json(
      { success: false, error: `Invalid defaultMapType. Must be one of: ${VALID_MAP_TYPES.join(', ')}` },
      { status: 400 }
    );
  }
  if (
    updatePayload.accident_sensor_sensitivity !== undefined &&
    typeof updatePayload.accident_sensor_sensitivity === 'string' &&
    !(VALID_SENSOR_SENSITIVITIES as readonly string[]).includes(updatePayload.accident_sensor_sensitivity) &&
    !/^custom_\d+(\.\d+)?$/.test(updatePayload.accident_sensor_sensitivity)
  ) {
    return NextResponse.json(
      { success: false, error: `Invalid accidentSensorSensitivity. Must be one of: ${VALID_SENSOR_SENSITIVITIES.join(', ')}, or a custom threshold (custom_N)` },
      { status: 400 }
    );
  }

  const authHeader = request.headers.get('authorization') || '';
  const expected = `Bearer ${process.env.DEVICE_API_SECRET}`;
  const isDevice = authHeader.length === expected.length && timingSafeEqual(Buffer.from(authHeader), Buffer.from(expected));

  let supabase;
  let user = null;
  if (isDevice) {
    supabase = getSupabaseAdmin();
  } else {
    supabase = await getSupabaseServerClient(request);
    const { data: authData, error: authError } = await getAuthenticatedUser(request, supabase);
    if (authError || !authData.user) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }
    user = authData.user;
  }

  const adminSupabase = getSupabaseAdmin();
  
  // Verify ownership before upsert
  const { data: existingDevice } = await adminSupabase
    .from('devices')
    .select('user_id')
    .eq('id', serialNumber)
    .maybeSingle();

  if (existingDevice && existingDevice.user_id) {
    if (!user || user.id !== existingDevice.user_id) {
      return NextResponse.json({ success: false, error: 'Forbidden. Device is linked to another account.' }, { status: 403 });
    }
  }

  // Set user_id if we have an authenticated user
  if (user) {
    updatePayload.user_id = user.id;
  }

  const { error } = await adminSupabase.from('devices').upsert(
    updatePayload,
    { onConflict: 'id', ignoreDuplicates: false }
  );

  if (error) {
    console.error('[POST /api/devices/config] Upsert error:', error);
    return NextResponse.json(
      { success: false, error: 'Database error' },
      { status: 500 }
    );
  }

  return NextResponse.json({ success: true, message: 'Device configuration saved' });
}
