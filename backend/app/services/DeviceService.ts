import { getSupabaseAdmin } from '../lib/supabase';
import { DeviceWithLatestLocation, DeviceRecord, LocationRecord } from '../types/telemetry';

export class DeviceService {
  /**
   * Fetches all registered devices and merges their latest coordinates/telemetry.
   */
  static async getDevicesWithLatestLocation(): Promise<DeviceWithLatestLocation[]> {
    try {
      const supabase = getSupabaseAdmin();
      
      // 1. Fetch devices ordered by last seen
      const { data: devices, error: devicesError } = await supabase
        .from('devices')
        .select('*')
        .order('last_seen_at', { ascending: false, nullsFirst: false });

      if (devicesError) throw devicesError;
      if (!devices || devices.length === 0) return [];

      // 2. Fetch recent location records for these devices
      const deviceIds = devices.map((d: DeviceRecord) => d.id);
      const { data: latestLocations, error: locationsError } = await supabase
        .from('locations')
        .select('*')
        .in('device_id', deviceIds)
        .order('synced_at', { ascending: false });

      if (locationsError) throw locationsError;

      // 3. Keep only the most recent location record per device
      const latestByDevice = new Map<string, LocationRecord>();
      for (const loc of (latestLocations ?? [])) {
        if (!latestByDevice.has(loc.device_id)) {
          latestByDevice.set(loc.device_id, loc);
        }
      }

      // 4. Merge device and location records
      return devices.map((device: DeviceRecord) => ({
        ...device,
        latestLocation: latestByDevice.get(device.id) ?? null,
      }));
    } catch (error) {
      console.error('Error in DeviceService.getDevicesWithLatestLocation:', error);
      return [];
    }
  }
}
