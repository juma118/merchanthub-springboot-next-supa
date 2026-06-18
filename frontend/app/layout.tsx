import type { Metadata } from 'next';
import './globals.css';
import { Providers } from './providers';

export const metadata: Metadata = {
  title: 'MerchantHub',
  description: 'Multi-tenant e-commerce analytics dashboard',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="min-h-screen bg-[#0b0f1a] text-slate-200">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
