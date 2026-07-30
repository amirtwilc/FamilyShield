import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'FamilyShield',
  description: 'FamilyShield parental-safety API',
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
            <a href="/api/docs">API docs</a>
            <a href="/api/health">Health</a>
          </nav>
        </div>
        {children}
      </body>
    </html>
  );
}
