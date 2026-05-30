/**
 * Firebase Cloud Messaging (FCM) dispatcher.
 *
 * Initialises the Firebase Admin SDK once (singleton) and exposes
 * [sendSosPushToAll] to fan-out a high-priority push to every registered device.
 *
 * Design notes:
 *  - Initialisation is lazy and idempotent — safe to call from multiple requests concurrently.
 *  - FCM tokens are stored per-device in Supabase `devices.fcm_token`.
 *  - The sending device is excluded from the fan-out (it already knows it activated SOS).
 *  - Uses `sendEachForMulticast` so individual token failures don't block the rest.
 */

import * as admin from 'firebase-admin';
import { getSupabaseAdmin } from './supabase';

// ── Singleton initialisation ──────────────────────────────────────────────────

let adminInitialised = false;

function getFirebaseApp(): admin.app.App {
  if (adminInitialised) return admin.app();

  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!serviceAccountJson) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON env var is not set');
  }

  const serviceAccount = JSON.parse(serviceAccountJson) as admin.ServiceAccount;

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });

  adminInitialised = true;
  return admin.app();
}

// ── Public API ────────────────────────────────────────────────────────────────

interface SosPushData {
  senderDeviceId: string;   // excluded from push fan-out
  deviceLabel:    string;
  lat:            number;
  lng:            number;
  batteryLevel:   number;
  timestamp:      string;
}

/**
 * Fetches all FCM tokens from Supabase (excluding the SOS sender) and sends
 * a high-priority push notification to each registered device.
 */
export async function sendSosPushToAll(data: SosPushData): Promise<void> {
  const { senderDeviceId, deviceLabel, lat, lng, batteryLevel, timestamp } = data;

  // ── 1. Fetch all registered FCM tokens ─────────────────────────────────────
  const supabase = getSupabaseAdmin();
  const { data: devices, error } = await supabase
    .from('devices')
    .select('id, fcm_token')
    .not('fcm_token', 'is', null)
    .neq('id', senderDeviceId);   // Sender already knows — skip it

  if (error) {
    console.error('[FCM] Failed to fetch device tokens:', error.message);
    return;
  }

  const tokens = (devices ?? [])
    .map((d) => d.fcm_token as string)
    .filter(Boolean);

  if (tokens.length === 0) {
    console.log('[FCM] No other devices registered — nothing to push');
    return;
  }

  console.log(`[FCM] Sending SOS push to ${tokens.length} device(s)…`);

  // ── 2. Build the multicast message ─────────────────────────────────────────
  const mapLink = `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
  const timeStr = new Date(timestamp).toLocaleString('pt-PT');

  const message: admin.messaging.MulticastMessage = {
    tokens,
    notification: {
      title: `🚨 SOS: ${deviceLabel}`,
      body:  `Bateria: ${batteryLevel}% · ${timeStr}`,
    },
    data: {
      type:        'SOS',
      deviceLabel,
      lat:         String(lat),
      lng:         String(lng),
      batteryLevel: String(batteryLevel),
      timestamp,
      mapLink,
    },
    android: {
      priority: 'high',
      notification: {
        channelId:   'sos_push_channel',   // matches FcmService.SOS_CHANNEL_ID
        priority:    'max',
        defaultVibrateTimings: false,
        vibrateTimingsMillis:  [0, 500, 200, 500, 200, 500],
      },
    },
  };

  // ── 3. Fan-out ──────────────────────────────────────────────────────────────
  try {
    const app = getFirebaseApp();
    const result = await app.messaging().sendEachForMulticast(message);

    const successCount = result.responses.filter((r) => r.success).length;
    const failCount    = result.responses.filter((r) => !r.success).length;

    console.log(`[FCM] Push complete — ✅ ${successCount} delivered, ❌ ${failCount} failed`);

    // Log individual failures (e.g. stale tokens) for debugging
    result.responses.forEach((r, i) => {
      if (!r.success) {
        console.warn(`[FCM] Token[${i}] failed:`, r.error?.message);
      }
    });
  } catch (err) {
    console.error('[FCM] sendEachForMulticast error:', err);
  }
}
