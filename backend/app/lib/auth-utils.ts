import { SupabaseClient } from '@supabase/supabase-js';

export async function getAuthenticatedUser(request: Request, supabase: SupabaseClient) {
  const authHeader = request.headers.get('authorization');
  const token = authHeader?.match(/^Bearer\s+(.+)$/i)?.[1];
  
  if (token) {
    return await supabase.auth.getUser(token);
  }
  
  return await supabase.auth.getUser();
}
