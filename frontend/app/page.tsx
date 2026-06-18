'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/useAuth';
import { Spinner } from '@/components/ui';

export default function Home() {
  const router = useRouter();
  const { token, ready } = useAuth();

  useEffect(() => {
    if (!ready) return;
    router.replace(token ? '/dashboard' : '/login');
  }, [ready, token, router]);

  return (
    <div className="flex min-h-screen items-center justify-center text-slate-400">
      <Spinner />
    </div>
  );
}
