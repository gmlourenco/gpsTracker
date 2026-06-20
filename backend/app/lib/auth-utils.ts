import { SupabaseClient } from '@supabase/supabase-js';

export async function getAuthenticatedUser(request: Request, supabase: SupabaseClient) {
  const authHeader = request.headers.get('authorization');
  const token = authHeader?.startsWith('Bearer ') ? authHeader.replace('Bearer ', '') : undefined;
  
  if (token) {
    return await supabase.auth.getUser(token);
  }
  
  return await supabase.auth.getUser();
}
