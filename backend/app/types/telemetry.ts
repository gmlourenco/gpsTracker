/**
 * TypeScript payload contracts for Segurança Rural GPS Tracker API.
 *
 * These interfaces mirror the KMP TelemetryRecord data class in the
 * shared module and must be kept in sync when fields are added/removed.
 */

// ── GPS sub-object ──────────────────────────────────────────────────────────

export interface GpsData {
  /** Latitude in decimal degrees (WGS84) */
  lat: number;
  /** Longitude in decimal degrees (WGS84) */
  lng: number;
  /** Estimated horizontal accuracy radius in metres */
  accuracy: number;
  /** Ground speed in km/h */
  speed: number;
  /** Compass heading in degrees (0–360) */
  heading: number;
}

// ── Full telemetry payload (POST /api/location) ─────────────────────────────

export interface TelemetryPayload {
  /** UUID generated on first device setup, stored in EncryptedSharedPreferences */
  deviceId: string;
  /** Human-readable device label (e.g., "Trator-Pai") */
  deviceLabel: string;
  /** ISO 8601 timestamp from the device at point of capture */
  timestamp: string;
  /** Device battery level 0–100 */
  batteryLevel: number;
  /** Whether the device is currently charging */
  batteryCharging: boolean;
  /** GPS coordinates and motion data */
  gps: GpsData;
  /** Whether the SOS emergency mode is active */
  emergencyState: boolean;
  /** Whether GPS tracking is enabled by the user */
  trackingEnabled: boolean;
  /** Network type at time of transmission (e.g., "4G", "3G", "WIFI") */
  networkType: string;
  /** App version string for debugging and compatibility checks */
  appVersion: string;
}

// ── Emergency-only payload (POST /api/emergency) ────────────────────────────

export interface EmergencyPayload {
  /** UUID of the device triggering SOS */
  deviceId: string;
  /** Device label for immediate dashboard display */
  deviceLabel: string;
  /** ISO 8601 timestamp of SOS activation */
  timestamp: string;
  /** Battery level at time of SOS (critical for response planning) */
  batteryLevel: number;
  /** Whether the device is charging */
  batteryCharging: boolean;
  /** Last known GPS position at SOS activation */
  gps: GpsData;
  /** Network type at time of SOS */
  networkType: string;
  /** App version */
  appVersion: string;
}

// ── Database row shapes (returned by Supabase queries) ──────────────────────

export interface DeviceRecord {
  id: string;
  label: string;
  created_at: string;
  last_seen_at: string | null;
  tracking_enabled: boolean;
  app_version: string;
}

export interface LocationRecord {
  id: number;
  device_id: string;
  lat: number;
  lng: number;
  accuracy: number;
  speed: number;
  heading: number;
  battery_level: number;
  battery_charging: boolean;
  emergency_state: boolean;
  network_type: string;
  tracking_enabled: boolean;
  app_version: string;
  created_at: string;
  synced_at: string;
}

// ── API response shapes ──────────────────────────────────────────────────────

export interface DeviceWithLatestLocation extends DeviceRecord {
  /** Most recent location record for this device, null if never seen */
  latestLocation: LocationRecord | null;
}

export interface ApiSuccessResponse {
  success: true;
  message: string;
}

export interface ApiErrorResponse {
  success: false;
  error: string;
  details?: string;
}

export type ApiResponse = ApiSuccessResponse | ApiErrorResponse;

// ── Validation helpers ───────────────────────────────────────────────────────

/** Validates that a value is a plausible UUID string */
export function isValidUuid(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

/** Validates that a value is a plausible ISO 8601 timestamp */
export function isValidIsoTimestamp(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  const d = new Date(value);
  return !isNaN(d.getTime());
}

/** Returns human-readable validation failures (empty array = valid). */
export function getTelemetryValidationErrors(body: unknown): string[] {
  if (typeof body !== 'object' || body === null) {
    return ['body must be a JSON object'];
  }

  const p = body as Record<string, unknown>;
  const errors: string[] = [];

  if (!isValidUuid(p.deviceId)) {
    errors.push(
      'deviceId must be a UUID string (v1–v5). Android nameUUIDFromBytes produces v3 — do not require v4 only.'
    );
  }
  if (typeof p.deviceLabel !== 'string' || p.deviceLabel.trim() === '') {
    errors.push('deviceLabel must be a non-empty string');
  }
  if (!isValidIsoTimestamp(p.timestamp)) {
    errors.push('timestamp must be a valid ISO 8601 string');
  }
  if (typeof p.batteryLevel !== 'number' || p.batteryLevel < 0 || p.batteryLevel > 100) {
    errors.push('batteryLevel must be a number between 0 and 100');
  }
  if (typeof p.batteryCharging !== 'boolean') {
    errors.push('batteryCharging must be a boolean');
  }
  if (typeof p.emergencyState !== 'boolean') {
    errors.push('emergencyState must be a boolean');
  }
  if (typeof p.trackingEnabled !== 'boolean') {
    errors.push('trackingEnabled must be a boolean');
  }
  if (typeof p.networkType !== 'string') {
    errors.push('networkType must be a string');
  }
  if (typeof p.appVersion !== 'string') {
    errors.push('appVersion must be a string');
  }

  const gps = p.gps as Record<string, unknown>;
  if (typeof gps !== 'object' || gps === null) {
    errors.push('gps must be an object');
  } else {
    if (typeof gps.lat !== 'number' || gps.lat < -90 || gps.lat > 90) {
      errors.push('gps.lat must be a number between -90 and 90');
    }
    if (typeof gps.lng !== 'number' || gps.lng < -180 || gps.lng > 180) {
      errors.push('gps.lng must be a number between -180 and 180');
    }
    if (typeof gps.accuracy !== 'number' || gps.accuracy < 0) {
      errors.push('gps.accuracy must be a non-negative number');
    }
    if (typeof gps.speed !== 'number' || gps.speed < 0) {
      errors.push('gps.speed must be a non-negative number');
    }
    if (typeof gps.heading !== 'number') {
      errors.push('gps.heading must be a number');
    }
  }

  return errors;
}

/** Validates a complete TelemetryPayload object */
export function validateTelemetryPayload(body: unknown): body is TelemetryPayload {
  return getTelemetryValidationErrors(body).length === 0;
}

/** Validates an EmergencyPayload object */
export function validateEmergencyPayload(body: unknown): body is EmergencyPayload {
  if (typeof body !== 'object' || body === null) return false;
  const p = body as Record<string, unknown>;

  if (!isValidUuid(p.deviceId)) return false;
  if (typeof p.deviceLabel !== 'string' || p.deviceLabel.trim() === '') return false;
  if (!isValidIsoTimestamp(p.timestamp)) return false;
  if (typeof p.batteryLevel !== 'number') return false;
  if (typeof p.batteryCharging !== 'boolean') return false;
  if (typeof p.networkType !== 'string') return false;
  if (typeof p.appVersion !== 'string') return false;

  const gps = p.gps as Record<string, unknown>;
  if (typeof gps !== 'object' || gps === null) return false;
  if (typeof gps.lat !== 'number') return false;
  if (typeof gps.lng !== 'number') return false;
  if (typeof gps.accuracy !== 'number') return false;
  if (typeof gps.speed !== 'number') return false;
  if (typeof gps.heading !== 'number') return false;

  return true;
}
