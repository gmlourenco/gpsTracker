/**
 * Notification service to dispatch Twilio WhatsApp alerts and Resend Email alerts.
 * Uses native fetch to avoid heavy npm dependencies and keep functions fast on Vercel.
 */

interface EmergencyNotificationData {
  deviceLabel: string;
  lat: number;
  lng: number;
  batteryLevel: number;
  timestamp: string;
}

/**
 * Dispatches emergency alerts via WhatsApp (Twilio) and Email (Resend) if credentials exist in env.
 */
export async function sendEmergencyNotifications(data: EmergencyNotificationData): Promise<void> {
  const { deviceLabel, lat, lng, batteryLevel, timestamp } = data;
  const mapLink = `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
  const timeStr = new Date(timestamp).toLocaleString('pt-PT');

  const promises: Promise<unknown>[] = [];

  // ── 1. WhatsApp Notification via Twilio REST API ──────────────────────────
  const twilioSid = process.env.TWILIO_ACCOUNT_SID;
  const twilioAuthToken = process.env.TWILIO_AUTH_TOKEN;
  const twilioFrom = process.env.TWILIO_FROM_WHATSAPP; // e.g. "whatsapp:+14155238886"
  const twilioTo = process.env.TWILIO_TO_WHATSAPP;     // e.g. "whatsapp:+351XXXXXXXXX"

  if (twilioSid && twilioAuthToken && twilioFrom && twilioTo) {
    console.log(`[NotificationService] Dispatching WhatsApp alert to ${twilioTo} via Twilio...`);
    const twilioUrl = `https://api.twilio.com/2010-04-01/Accounts/${twilioSid}/Messages.json`;
    const authHeader = 'Basic ' + Buffer.from(`${twilioSid}:${twilioAuthToken}`).toString('base64');
    
    const body = new URLSearchParams({
      From: twilioFrom,
      To: twilioTo,
      Body: `🚨 ALERTA SOS: O dispositivo "${deviceLabel}" ativou o modo de emergência!\n🔋 Bateria: ${batteryLevel}%\n📅 Hora: ${timeStr}\n📍 Localização: ${mapLink}`,
    });

    promises.push(
      fetch(twilioUrl, {
        method: 'POST',
        headers: {
          'Authorization': authHeader,
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: body.toString(),
      })
        .then(async (res) => {
          if (!res.ok) {
            const errText = await res.text();
            throw new Error(`Twilio returned HTTP ${res.status}: ${errText}`);
          }
          console.log('[NotificationService] WhatsApp alert sent successfully');
        })
        .catch((err) => {
          console.error('[NotificationService] Twilio WhatsApp error:', err);
        })
    );
  } else {
    console.log('[NotificationService] WhatsApp disabled (missing Twilio configuration)');
  }

  // ── 2. Email Notification via Resend API ──────────────────────────────────
  const resendApiKey = process.env.RESEND_API_KEY;
  const alertEmailTo = process.env.ALERT_RECIPIENT_EMAIL;

  if (resendApiKey && alertEmailTo) {
    console.log(`[NotificationService] Dispatching Email alert to ${alertEmailTo} via Resend...`);
    
    promises.push(
      fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${resendApiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          from: 'Segurança Rural <alerts@segurancarural.app>',
          to: alertEmailTo,
          subject: `🚨 ALERTA SOS: Emergência Ativada por ${deviceLabel}`,
          html: `
            <div style="font-family: system-ui, -apple-system, sans-serif; padding: 20px; color: #1E293B; max-width: 600px; margin: 0 auto; border: 1.5px solid #F1F5F9; border-radius: 8px;">
              <h2 style="color: #DC2626; margin-top: 0;">🚨 Alerta de Emergência SOS</h2>
              <p>O dispositivo <strong>${deviceLabel}</strong> ativou o modo de emergência SOS!</p>
              <div style="background-color: #F8FAFC; padding: 12px 16px; border-radius: 6px; margin: 16px 0;">
                <p style="margin: 4px 0;"><strong>Bateria:</strong> ${batteryLevel}%</p>
                <p style="margin: 4px 0;"><strong>Data/Hora:</strong> ${timeStr}</p>
                <p style="margin: 4px 0;"><strong>Coordenadas:</strong> ${lat}, ${lng}</p>
              </div>
              <p style="margin-bottom: 24px;">Por favor, aja com urgência para prestar socorro.</p>
              <a href="${mapLink}" target="_blank" style="display: inline-block; padding: 12px 20px; background-color: #DC2626; color: #FFFFFF; text-decoration: none; border-radius: 6px; font-weight: bold; text-align: center;">
                ➔ Ver Localização no Google Maps
              </a>
            </div>
          `,
        }),
      })
        .then(async (res) => {
          if (!res.ok) {
            const errText = await res.text();
            throw new Error(`Resend returned HTTP ${res.status}: ${errText}`);
          }
          console.log('[NotificationService] Email alert sent successfully');
        })
        .catch((err) => {
          console.error('[NotificationService] Resend Email error:', err);
        })
    );
  } else {
    console.log('[NotificationService] Email disabled (missing Resend configuration)');
  }

  // Await all notification dispatches concurrently
  if (promises.length > 0) {
    await Promise.all(promises);
  }
}
