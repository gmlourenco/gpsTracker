import { randomInt } from 'crypto';

const CHARSET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 30 chars, no ambiguous I/O/1/0

export function generateInviteCode(length = 8): string {
  let result = '';
  for (let i = 0; i < length; i++) {
    result += CHARSET.charAt(randomInt(CHARSET.length));
  }
  return result;
}

export const INVITE_DEFAULTS = {
  MAX_USES: 1,
  EXPIRY_DAYS: 7,
} as const;
