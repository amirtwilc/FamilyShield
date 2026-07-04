import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'FamilyShield',
  description: 'Parental-safety dashboard & device simulator',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="topbar">
          <span className="brand">
            FamilyShield<small>parental safety</small>
          </span>
          <nav className="row">
            <a href="/parent">Parent</a>
            <a href="/kid">Kid device</a>
            <a href="/api/docs">API docs</a>
          </nav>
        </div>
        {children}
      </body>
    </html>
  );
}
