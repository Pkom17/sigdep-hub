import { ReactNode } from 'react';

/**
 * Page header with a teal accent bar, the title and an optional subtitle.
 * Pages put their action toolbar (filters, export button) in `right`.
 *
 *   <PageHeader title="Biologie" subtitle="..."
 *               right={<select ... /><button ... />} />
 */
export function PageHeader({
  title,
  subtitle,
  right,
}: Readonly<{
  title: ReactNode;
  subtitle?: ReactNode;
  right?: ReactNode;
}>) {
  return (
    <div className="mb-6 pb-4 border-b border-slate-200">
      <div className="flex items-stretch gap-3">
        <span aria-hidden="true"
              className="w-1 rounded-full bg-sigdep-500 self-stretch shrink-0" />
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-sigdep-900">
            {title}
          </h1>
          {subtitle && (
            <p className="mt-0.5 text-sm text-ink-muted">{subtitle}</p>
          )}
        </div>
      </div>
      {right && (
        <div className="mt-3 flex items-center gap-3 flex-wrap">{right}</div>
      )}
    </div>
  );
}
