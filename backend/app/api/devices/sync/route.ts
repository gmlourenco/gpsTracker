import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

export async function POST(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    if (userError || !user) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();
    const { deviceId, config } = body;

    // 1. If deviceId is provided, we try to claim it and/or upsert its config.
    if (deviceId && config) {
      // Use admin client to allow reclaiming the device if the user logged in with a new account (e.g., from anonymous to Google)
      const adminSupabase = getSupabaseAdmin();

      const devicePayload: {
        id: string;
        user_id: string;
        label: string;
        marker_color: string;
        tracking_enabled: boolean;
        app_version: string;
        farm_id?: string;
      } = {
        id: deviceId,
        user_id: user.id,
        label: config.label || 'Unknown Device',
        marker_color: config.markerColor || '#16A34A',
        tracking_enabled: config.trackingEnabled ?? true,
        app_version: config.appVersion || '1.0.0'
      };

      if (config.farmId) {
        // Garantir que o utilizador pertence à farm que está a reclamar
        const { data: memberCheck } = await adminSupabase
          .from('farm_members')
          .select('id')
          .eq('farm_id', config.farmId)
          .eq('user_id', user.id)
          .single();
          
        if (!memberCheck) {
          return NextResponse.json({ success: false, error: 'Forbidden: You do not belong to this farm' }, { status: 403 });
        }
        devicePayload.farm_id = config.farmId;
      }

      const { error: upsertError } = await adminSupabase
        .from('devices')
        .upsert(devicePayload);

      if (upsertError) {
        console.error('Error upserting device:', upsertError);
        return NextResponse.json({ success: false, error: 'Failed to sync device' }, { status: 500 });
      }
    }

    // 2. Fetch all devices for this user
    const { data: devices, error: fetchError } = await supabase
      .from('devices')
      .select('id, label, marker_color, tracking_enabled, farm_id')
      .eq('user_id', user.id);

    if (fetchError) {
      console.error('Error fetching user devices:', fetchError);
      return NextResponse.json({ success: false, error: 'Failed to fetch devices' }, { status: 500 });
    }

    return NextResponse.json({
      success: true,
      devices: devices || []
    });

  } catch (error) {
    console.error('Device sync exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
