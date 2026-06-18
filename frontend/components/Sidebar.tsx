'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const links = [
  { href: '/dashboard', label: 'Dashboard', icon: '◧' },
  { href: '/products', label: 'Products', icon: '▦' },
  { href: '/inventory', label: 'Inventory', icon: '▤' },
  { href: '/orders', label: 'Orders', icon: '🧾' },
  { href: '/alerts', label: 'Alerts', icon: '🔔' },
  { href: '/sync', label: 'Sync', icon: '⟳' },
];

export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="hidden w-60 shrink-0 flex-col border-r border-white/10 bg-slate-950/60 backdrop-blur md:flex">
      <div className="flex items-center gap-2 px-5 py-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 text-sm font-bold text-white shadow-lg shadow-indigo-500/30">
          M
        </div>
        <span className="text-lg font-bold text-slate-100">MerchantHub</span>
      </div>
      <nav className="flex flex-1 flex-col gap-0.5 px-3 py-2">
        {links.map((l) => {
          const active = pathname === l.href || pathname.startsWith(`${l.href}/`);
          return (
            <Link
              key={l.href}
              href={l.href}
              className={`relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                active
                  ? 'bg-white/10 text-white'
                  : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
              }`}
            >
              {active && (
                <span className="absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-gradient-to-b from-indigo-400 to-violet-400" />
              )}
              <span className="w-4 text-center text-base leading-none">{l.icon}</span>
              {l.label}
            </Link>
          );
        })}
      </nav>
      <div className="px-5 py-4 text-xs text-slate-500">v0.1.0</div>
    </aside>
  );
}
