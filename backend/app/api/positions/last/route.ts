/**
 * GET /api/positions/last
 *
 * Returns every device with its most recent known position.
 * Uses the get_latest_positions() PostgreSQL function for efficient lookup.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { mapRpcRowsToDevicesWithHistory } from '../../../lib/mappers';
import { DeviceWithLatestLocation } from '../../../types/telemetry';
import { createClient } from '@supabase/supabase-js';

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = new URL(request.url);
  const historyParam = searchParams.get('history');
  const historyCount = historyParam ? parseInt(historyParam, 10) : 0;

  const authHeader = request.headers.get('authorization') || '';
  let supabase;

  if (authHeader.startsWith('Bearer eyJ')) {
    supabase = createClient(
      process.env.NEXT_PUBLIC_SUPABASE_URL!,
      process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
      {
        global: {
          headers: {
            Authorization: authHeader,
          },
        },
      }
    );
  } else {
    supabase = getSupabaseAdmin();
  }

  // ── 1. Call optimized RPC ──────────────────────────────────────────────────
  const { data: rows, error: rpcError } = await supabase.rpc('get_positions_with_history', {
    p_history: isNaN(historyCount) ? 0 : historyCount
  });

  if (rpcError) {
    console.error('[GET /api/positions/last] RPC error:', rpcError);
    return NextResponse.json(
      { success: false, error: 'Database error' },
      { status: 500 }
    );
  }

  if (!rows || rows.length === 0) {
    return NextResponse.json({
      success: true,
      fetchedAt: new Date().toISOString(),
      devices: [] as DeviceWithLatestLocation[],
    });
  }

  // ── 2. Transform RPC rows ──────────────────────────────────────────────────
  const devices: DeviceWithLatestLocation[] = mapRpcRowsToDevicesWithHistory(rows);

  const response = NextResponse.json({
    success: true,
    fetchedAt: new Date().toISOString(),
    devices,
  });
  response.headers.set('Cache-Control', 'private, max-age=10');
  return response;
}

