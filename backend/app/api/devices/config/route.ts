/**
 * GET & POST /api/devices/config
 *
 * GET: Retrieves saved configurations for a given device serialNumber.
 * POST: Saves or updates configuration parameters for a device.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { isValidSerialNumber } from '../../../types/telemetry';

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const serialNumber = searchParams.get('serialNumber');

  if (!serialNumber || !isValidSerialNumber(serialNumber)) {
    return NextResponse.json(
      { success: false, error: 'Invalid or missing serialNumber parameter' },
      { status: 400 }
    );
  }

  const supabase = getSupabaseAdmin();
  const { data, error } = await supabase
    .from('devices')
    .select('*')
    .eq('id', serialNumber)
    .maybeSingle();

  if (error) {
    console.error('[GET /api/devices/config] Database error:', error);
    return NextResponse.json(
      { success: false, error: 'Database error', details: error.message },
      { status: 500 }
    );
  }

  if (!data) {
    return NextResponse.json({ success: true, config: null });
  }

  return NextResponse.json({
    success: true,
    config: {
      serialNumber: data.id,
      deviceLabel: data.label || 'Dispositivo',
      markerColor: data.marker_color,
      trackingIntervalMs: data.tracking_interval_ms !== null ? Number(data.tracking_interval_ms) : 60000,
      trackingDistanceM: data.tracking_distance_m !== null ? Number(data.tracking_distance_m) : 200,
      defaultMapType: data.default_map_type || 'SATELLITE',
      accidentSensorSensitivity: data.accident_sensor_sensitivity || 'medium',
      configUpdatedAt: data.config_updated_at !== null ? Number(data.config_updated_at) : -1,
    }
  });
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

  const supabase = getSupabaseAdmin();
  const { error } = await supabase.from('devices').upsert(
    updatePayload,
    { onConflict: 'id', ignoreDuplicates: false }
  );

  if (error) {
    console.error('[POST /api/devices/config] Upsert error:', error);
    return NextResponse.json(
      { success: false, error: 'Database error', details: error.message },
      { status: 500 }
    );
  }

  return NextResponse.json({ success: true, message: 'Device configuration saved' });
}
