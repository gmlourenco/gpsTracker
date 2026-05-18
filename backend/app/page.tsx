import { DeviceWithLatestLocation, DeviceRecord } from './types/telemetry';
import styles from './page.module.css';

import MapWrapper from './components/MapWrapper';

export const dynamic = 'force-dynamic';

import { getSupabaseAdmin } from './lib/supabase';

// Since this is a server component, we can query the database directly 
// which is much safer and faster than doing an HTTP fetch to our own API route!
async function getDevices(): Promise<DeviceWithLatestLocation[]> {
  try {
    const supabase = getSupabaseAdmin();
    const { data: devices, error: devicesError } = await supabase
      .from('devices')
      .select('*')
      .order('last_seen_at', { ascending: false, nullsFirst: false });

    if (devicesError) throw devicesError;
    if (!devices || devices.length === 0) return [];

    const deviceIds = devices.map((d: DeviceRecord) => d.id);
    const { data: latestLocations, error: locationsError } = await supabase
      .from('locations')
      .select('*')
      .in('device_id', deviceIds)
      .order('synced_at', { ascending: false });

    if (locationsError) throw locationsError;

    const latestByDevice = new Map();
    for (const loc of (latestLocations ?? [])) {
      if (!latestByDevice.has(loc.device_id)) {
        latestByDevice.set(loc.device_id, loc);
      }
    }

    return devices.map((device: DeviceRecord) => ({
      ...device,
      latestLocation: latestByDevice.get(device.id) ?? null,
    }));
  } catch (error) {
    console.error('Error fetching devices:', error);
    return [];
  }
}

export default async function Dashboard() {
  const devices = await getDevices();
  // eslint-disable-next-line react-hooks/purity
  const now = Date.now(); // Calculate once per render to satisfy react-hooks/purity

  const getStatus = (device: DeviceWithLatestLocation) => {
    if (device.latestLocation?.emergency_state) return 'sos';
    const lastSeen = new Date(device.last_seen_at || 0).getTime();
    if ((now - lastSeen) > 60 * 60 * 1000) return 'offline';
    return 'active';
  };

  return (
    <div className={styles.dashboard}>
      <header className={styles.header}>
        <div className={styles.logoContainer}>
          <div className={styles.pulseDot}></div>
          <h1>Segurança Rural</h1>
        </div>
        <p className={styles.subtitle}>Live Tracking Dashboard</p>
      </header>

      <main className={styles.mainContent}>
        <aside className={styles.sidebar}>
          <h2 className={styles.sidebarTitle}>Devices ({devices.length})</h2>
          
          <div className={styles.deviceList}>
            {devices.map(device => {
              const status = getStatus(device);
              return (
                <div key={device.id} className={`${styles.deviceCard} ${styles[status]}`}>
                  <div className={styles.deviceHeader}>
                    <h3>{device.label}</h3>
                    <span className={styles.statusBadge}>{status.toUpperCase()}</span>
                  </div>
                  
                  <div className={styles.deviceDetails}>
                    <p>🔋 {device.latestLocation?.battery_level ?? '--'}%</p>
                    <p>📍 {device.latestLocation?.speed?.toFixed(1) ?? '--'} km/h</p>
                  </div>
                </div>
              );
            })}
            
            {devices.length === 0 && (
              <p className={styles.emptyState}>No devices registered yet.</p>
            )}
          </div>
        </aside>

        <section className={styles.mapSection}>
          <MapWrapper devices={devices} />
        </section>
      </main>
    </div>
  );
}
