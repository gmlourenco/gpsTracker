/**
 * Supabase singleton client factory for Segurança Rural backend.
 *
 * Two clients are exported:
 *   - `supabasePublic`  – uses the publishable (anon) key, safe for client-side
 *   - `supabaseAdmin`   – uses the service_role key, server-side only
 *
 * The admin client bypasses RLS and is used exclusively inside API Routes.
 * It must NEVER be imported in Client Components or exposed to the browser.
 */

import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { createServerClient } from '@supabase/ssr';
import { cookies } from 'next/headers';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const supabasePublishableKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!;
const supabaseServiceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY!;

if (!supabaseUrl) {
  throw new Error('Missing environment variable: NEXT_PUBLIC_SUPABASE_URL');
}
if (!supabasePublishableKey) {
  throw new Error('Missing environment variable: NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY');
}

/**
 * Public Supabase client — uses the publishable key.
 * Safe for client-side usage. Subject to RLS policies.
 */
export const supabasePublic: SupabaseClient = createClient(
  supabaseUrl,
  supabasePublishableKey
);

/**
 * Admin Supabase client — uses the service_role key.
 * SERVER-SIDE ONLY. Bypasses RLS. Do not import in Client Components.
 */
let _adminClient: SupabaseClient | null = null;

export function getSupabaseAdmin(): SupabaseClient {
  if (_adminClient) return _adminClient;
  if (!supabaseServiceRoleKey) {
    throw new Error(
      'Missing environment variable: SUPABASE_SERVICE_ROLE_KEY. ' +
      'Add it to .env.local and to your Vercel project environment variables.'
    );
  }
  _adminClient = createClient(supabaseUrl, supabaseServiceRoleKey, {
    auth: {
      // Service role clients should never persist sessions
      autoRefreshToken: false,
      persistSession: false,
    },
  });
  return _adminClient;
}

/**
 * Authenticated Server Client — uses the publishable key and user session.
 * Automatically applies RLS based on the user's cookies or Authorization header.
 */
export async function getSupabaseServerClient(request?: Request) {
  const cookieStore = await cookies();
  const authHeader = request?.headers.get('authorization');
  const token = authHeader?.match(/^Bearer\s+(.+)$/i)?.[1];
  
  // Do NOT forward our internal device secret to Supabase as it's not a valid JWT
  const isDeviceSecret = token === process.env.DEVICE_API_SECRET;
  
  return createServerClient(
    supabaseUrl,
    supabasePublishableKey,
    {
      global: {
        headers: (token && !isDeviceSecret) ? { Authorization: `Bearer ${token}` } : undefined,
      },
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll(cookiesToSet) {
          try {
            cookiesToSet.forEach(({ name, value, options }) =>
              cookieStore.set(name, value, options)
            );
          } catch {
            // Ignore error in server components
          }
        },
      },
    }
  );
}
