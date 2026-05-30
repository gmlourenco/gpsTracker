import appReleaseConfig from '../config/app-release.json';

export interface AppVersionInfo {
  latestVersion: string;
  minVersion: string;
  downloadUrl: string;
  releaseNotes: string;
}

/**
 * App version manifest for OTA updates.
 * Vercel env vars override app-release.json (set APP_LATEST_VERSION, etc. in project settings).
 */
export function getAppVersionInfo(): AppVersionInfo {
  return {
    latestVersion:
      process.env.APP_LATEST_VERSION?.trim() || appReleaseConfig.latestVersion,
    minVersion: process.env.APP_MIN_VERSION?.trim() || appReleaseConfig.minVersion,
    downloadUrl:
      process.env.APP_DOWNLOAD_URL?.trim() || appReleaseConfig.downloadUrl,
    releaseNotes:
      process.env.APP_RELEASE_NOTES?.trim() || appReleaseConfig.releaseNotes,
  };
}

/** Compare dotted semver segments (e.g. 1.5.0 vs 1.4.2). Returns 1 if a>b, -1 if a<b, 0 if equal. */
export function compareVersions(a: string, b: string): number {
  const parse = (v: string) =>
    v
      .replace(/^v/i, '')
      .split(/[.-]/)
      .map((part) => parseInt(part.replace(/[^0-9].*$/, ''), 10) || 0);

  const pa = parse(a);
  const pb = parse(b);
  const len = Math.max(pa.length, pb.length);

  for (let i = 0; i < len; i++) {
    const da = pa[i] ?? 0;
    const db = pb[i] ?? 0;
    if (da > db) return 1;
    if (da < db) return -1;
  }
  return 0;
}

export function isValidMarkerColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value);
}
