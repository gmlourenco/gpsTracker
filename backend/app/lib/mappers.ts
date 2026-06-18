/**
 * Shared row mappers for transforming Supabase RPC results into typed objects.
 *
 * Centralises the RPC row → DeviceWithLatestLocation mapping that was
 * previously duplicated in devices/route.ts, positions/last/route.ts,
 * and DeviceService.ts.
 */

import { DeviceWithLatestLocation } from '../types/telemetry';

/**
 * Maps a single row from get_latest_positions() RPC to DeviceWithLatestLocation.
 *
 * The RPC returns flat rows with aliased column names. This function
 * restructures them into the nested DeviceWithLatestLocation shape
 * expected by the frontend.
 */
export function mapRpcRowToDevice(r: Record<string, unknown>): DeviceWithLatestLocation {
  return {
    id: r.device_id as string,
    label: r.device_label as string,
    marker_color: r.marker_color as string,
    created_at: r.device_created_at as string,
    last_seen_at: r.last_seen_at as string | null,
    tracking_enabled: r.tracking_enabled as boolean,
    app_version: r.app_version as string,
    latestLocation: r.loc_id
      ? {
          id: r.loc_id as number,
          device_id: r.device_id as string,
          lat: r.lat as number,
          lng: r.lng as number,
          accuracy: r.accuracy as number,
          speed: r.speed as number,
          heading: r.heading as number,
          battery_level: r.battery_level as number,
          battery_charging: r.battery_charging as boolean,
          emergency_state: r.emergency_state as boolean,
          network_type: r.network_type as string,
          tracking_enabled: r.loc_tracking_enabled as boolean,
          app_version: r.loc_app_version as string,
          created_at: r.created_at as string,
          synced_at: r.synced_at as string,
        }
      : null,
  };
}

/**
 * Maps an array of RPC rows to DeviceWithLatestLocation[].
 * Convenience wrapper around mapRpcRowToDevice.
 */
export function mapRpcRowsToDevices(
  rows: Record<string, unknown>[]
): DeviceWithLatestLocation[] {
  return rows.map(mapRpcRowToDevice);
}
