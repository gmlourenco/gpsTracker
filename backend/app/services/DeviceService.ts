import { getSupabaseAdmin } from '../lib/supabase';
import { mapRpcRowsToDevices } from '../lib/mappers';
import { DeviceWithLatestLocation } from '../types/telemetry';

export class DeviceService {
  /**
   * Fetches all registered devices and their latest coordinates/telemetry.
   * Uses the get_latest_positions() PostgreSQL function for O(n_devices) performance.
   *
   * @throws Error if the database query fails (callers must handle)
   */
  static async getDevicesWithLatestLocation(): Promise<DeviceWithLatestLocation[]> {
    const supabase = getSupabaseAdmin();

    const { data: rows, error } = await supabase.rpc('get_latest_positions');

    if (error) {
      console.error('DeviceService.getDevicesWithLatestLocation RPC error:', error);
      throw error;
    }

    if (!rows || rows.length === 0) return [];

    return mapRpcRowsToDevices(rows);
  }
}

