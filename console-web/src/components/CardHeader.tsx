import { ReactNode } from 'react';

/**
 * Brand-tinted card header. Renders a teal stripe at the top of a `.card`
 * with the title and optional right-aligned slot (count, action, etc.).
 *
 *   <div className="card">
 *     <CardHeader title="…" right={<span>…</span>} />
 *     <div className="p-4">…</div>
 *   </div>
 */
export function CardHeader({
  title, right, subtitle,
}: Readonly<{
  title: ReactNode;
  subtitle?: ReactNode;
  right?: ReactNode;
}>) {
  return (
    <div className="card-header flex items-center justify-between gap-3">
      <div>
        <div>{title}</div>
        {subtitle && (
          <div className="text-xs font-normal text-sigdep-700/80 mt-0.5">{subtitle}</div>
        )}
      </div>
      {right}
    </div>
  );
}
