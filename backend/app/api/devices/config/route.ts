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
      emergencyContact: data.emergency_contact || '',
      syncOnMobileData: data.sync_on_mobile_data !== null ? data.sync_on_mobile_data : true,
      trackingIntervalMs: data.tracking_interval_ms !== null ? Number(data.tracking_interval_ms) : 60000,
      trackingDistanceM: data.tracking_distance_m !== null ? Number(data.tracking_distance_m) : 200,
      defaultMapType: data.default_map_type || 'SATELLITE',
      accidentSensorSensitivity: data.accident_sensor_sensitivity || 'medium',
      configUpdatedAt: data.config_updated_at !== null ? Number(data.config_updated_at) : -1,
    }
  });
}

export async function POST(request: NextRequest) {
  let body: any;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ success: false, error: 'Invalid JSON body' }, { status: 400 });
  }

  const {
    serialNumber,
    deviceLabel,
    markerColor,
    emergencyContact,
    syncOnMobileData,
    trackingIntervalMs,
    trackingDistanceM,
    defaultMapType,
    accidentSensorSensitivity,
    configUpdatedAt
  } = body;

  if (!serialNumber || !isValidSerialNumber(serialNumber)) {
    return NextResponse.json({ success: false, error: 'Invalid or missing serialNumber' }, { status: 400 });
  }

  // Validate remaining config parameters
  if (typeof deviceLabel !== 'string' || deviceLabel.trim() === '') {
    return NextResponse.json({ success: false, error: 'Invalid deviceLabel' }, { status: 400 });
  }
  if (typeof markerColor !== 'string' || !/^#[0-9A-Fa-f]{6}$/.test(markerColor)) {
    return NextResponse.json({ success: false, error: 'Invalid markerColor' }, { status: 400 });
  }
  if (typeof syncOnMobileData !== 'boolean') {
    return NextResponse.json({ success: false, error: 'Invalid syncOnMobileData' }, { status: 400 });
  }
  if (typeof trackingIntervalMs !== 'number') {
    return NextResponse.json({ success: false, error: 'Invalid trackingIntervalMs' }, { status: 400 });
  }
  if (typeof trackingDistanceM !== 'number') {
    return NextResponse.json({ success: false, error: 'Invalid trackingDistanceM' }, { status: 400 });
  }
  if (defaultMapType !== undefined && typeof defaultMapType !== 'string') {
    return NextResponse.json({ success: false, error: 'Invalid defaultMapType' }, { status: 400 });
  }
  if (accidentSensorSensitivity !== undefined && typeof accidentSensorSensitivity !== 'string') {
    return NextResponse.json({ success: false, error: 'Invalid accidentSensorSensitivity' }, { status: 400 });
  }
  if (configUpdatedAt !== undefined && typeof configUpdatedAt !== 'number') {
    return NextResponse.json({ success: false, error: 'Invalid configUpdatedAt' }, { status: 400 });
  }

  const supabase = getSupabaseAdmin();
  const { error } = await supabase.from('devices').upsert(
    {
      id: serialNumber,
      label: deviceLabel.trim(),
      marker_color: markerColor.toUpperCase(),
      emergency_contact: emergencyContact && emergencyContact.trim() !== '' ? emergencyContact.trim() : null,
      sync_on_mobile_data: syncOnMobileData,
      tracking_interval_ms: trackingIntervalMs,
      tracking_distance_m: trackingDistanceM,
      default_map_type: (defaultMapType || 'SATELLITE').trim().toUpperCase(),
      accident_sensor_sensitivity: (accidentSensorSensitivity || 'medium').trim().toLowerCase(),
      config_updated_at: configUpdatedAt !== undefined ? configUpdatedAt : -1,
    },
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
