/**
 * GET /api/app/version
 *
 * Returns the latest Android app version and download URL for in-app update checks.
 * Configure via Vercel env (APP_LATEST_VERSION, APP_DOWNLOAD_URL, …) or app-release.json.
 */

import { NextResponse } from 'next/server';
import { compareVersions, getAppVersionInfo } from '../../../lib/app-version';

export async function GET(request: Request): Promise<NextResponse> {
  const info = getAppVersionInfo();
  const clientVersion = new URL(request.url).searchParams.get('current')?.trim();

  let updateAvailable = false;
  let forceUpdate = false;

  if (clientVersion) {
    updateAvailable = compareVersions(info.latestVersion, clientVersion) > 0;
    forceUpdate = compareVersions(clientVersion, info.minVersion) < 0;
  }

  return NextResponse.json({
    success: true,
    latestVersion: info.latestVersion,
    minVersion: info.minVersion,
    downloadUrl: info.downloadUrl,
    releaseNotes: info.releaseNotes,
    updateAvailable,
    forceUpdate,
  });
}
