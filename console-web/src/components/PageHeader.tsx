import { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

/**
 * Page header with a teal accent bar, a title (optionally with a Lucide
 * icon), an optional subtitle, and an action toolbar (`right`) that drops
 * to a second row so growing controls don't push the title around.
 *
 *   <PageHeader icon={Stethoscope} title="Suivi clinique" subtitle="..."
 *               right={<GeoFilter ... /><button ... />} />
 *
 * `tone="admin"` swaps the teal accent for the indigo accent used across
 * the admin section (Synchronisation, Utilisateurs).
 */
export function PageHeader({
  title,
  subtitle,
  right,
  icon: Icon,
  tone = 'default',
}: Readonly<{
  title: ReactNode;
  subtitle?: ReactNode;
  right?: ReactNode;
  icon?: LucideIcon;
  tone?: 'default' | 'admin';
}>) {
  const bar = tone === 'admin' ? 'bg-accent-500' : 'bg-sigdep-500';
  const iconBg = tone === 'admin' ? 'bg-accent-50 text-accent-700' : 'bg-sigdep-50 text-sigdep-700';
  const titleColor = tone === 'admin' ? 'text-accent-800' : 'text-sigdep-900';

  return (
    <div className="mb-6 pb-4 border-b border-slate-200">
      <div className="flex items-stretch gap-3">
        <span aria-hidden="true"
              className={`w-1 rounded-full self-stretch shrink-0 ${bar}`} />
        <div className="flex items-center gap-3 min-w-0">
          {Icon && (
            <span className={`h-10 w-10 rounded-lg flex items-center justify-center shrink-0 ${iconBg}`}>
              <Icon className="h-5 w-5" />
            </span>
          )}
          <div className="min-w-0">
            <h1 className={`text-2xl font-bold tracking-tight ${titleColor}`}>
              {title}
            </h1>
            {subtitle && (
              <p className="mt-0.5 text-sm text-ink-muted">{subtitle}</p>
            )}
          </div>
        </div>
      </div>
      {right && (
        <div className="mt-3 flex items-center gap-3 flex-wrap">{right}</div>
      )}
    </div>
  );
}
