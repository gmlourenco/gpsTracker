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
export function getSupabaseAdmin(): SupabaseClient {
  if (!supabaseServiceRoleKey) {
    throw new Error(
      'Missing environment variable: SUPABASE_SERVICE_ROLE_KEY. ' +
      'Add it to .env.local and to your Vercel project environment variables.'
    );
  }
  return createClient(supabaseUrl, supabaseServiceRoleKey, {
    auth: {
      // Service role clients should never persist sessions
      autoRefreshToken: false,
      persistSession: false,
    },
  });
}
