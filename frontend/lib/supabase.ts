// Lazy, guarded Supabase client. Importing this module with empty env must NOT
// throw or create a client. Returns null when Supabase is not configured.

import type { SupabaseClient } from '@supabase/supabase-js';
import { env, isSupabaseConfigured } from './env';

let client: SupabaseClient | null = null;
let initialized = false;

export function getSupabase(): SupabaseClient | null {
  if (initialized) return client;
  initialized = true;

  if (!isSupabaseConfigured()) {
    client = null;
    return null;
  }

  // Import dynamically-friendly: createClient is tree-shakeable and only invoked
  // when env is present, so an empty-env build never constructs a client.
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { createClient } = require('@supabase/supabase-js');
  client = createClient(env.supabaseUrl, env.supabaseAnonKey, {
    auth: { persistSession: true, autoRefreshToken: true },
  });
  return client;
}
