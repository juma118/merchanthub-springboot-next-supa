'use client';

import type { ReactNode } from 'react';
import { ToasterProvider } from '@/components/Toaster';
import { AuthProvider } from '@/lib/useAuth';
import { AlertsProvider } from '@/lib/realtime';

// Order matters: Toaster first (used by realtime), then Auth (provides token),
// then Alerts (needs both).
export function Providers({ children }: { children: ReactNode }) {
  return (
    <ToasterProvider>
      <AuthProvider>
        <AlertsProvider>{children}</AlertsProvider>
      </AuthProvider>
    </ToasterProvider>
  );
}
