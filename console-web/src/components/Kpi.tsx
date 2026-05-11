import type { LucideIcon } from 'lucide-react';

export type KpiTone = 'positive' | 'neutral' | 'warning';

type Props = {
  label: string;
  value: string;
  hint?: string;
  hintTone?: KpiTone;
  /** Optional icon shown in the top-right corner. */
  icon?: LucideIcon;
};

const HINT_TEXT: Record<KpiTone, string> = {
  positive: 'text-emerald-600',
  neutral:  'text-slate-500',
  warning:  'text-amber-700',
};

const TOP_BAR: Record<KpiTone, string> = {
  positive: 'bg-emerald-500',
  neutral:  'bg-sigdep-500',
  warning:  'bg-amber-500',
};

const ICON_TINT: Record<KpiTone, string> = {
  positive: 'bg-emerald-50 text-emerald-600',
  neutral:  'bg-sigdep-50 text-sigdep-600',
  warning:  'bg-amber-50  text-amber-700',
};

export function Kpi({ label, value, hint, hintTone = 'neutral', icon: Icon }: Readonly<Props>) {
  return (
    <div className="card relative p-4 overflow-hidden hover:shadow-elevated transition-shadow">
      {/* Top accent bar — colour reflects the hint tone (positive/warning/neutral). */}
      <span aria-hidden="true"
            className={`absolute inset-x-0 top-0 h-1 ${TOP_BAR[hintTone]}`} />
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium text-ink-muted uppercase tracking-wide">{label}</p>
        {Icon && (
          <span className={`h-8 w-8 rounded-md flex items-center justify-center shrink-0
                            ${ICON_TINT[hintTone]}`}>
            <Icon className="h-4 w-4" />
          </span>
        )}
      </div>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-ink">{value}</p>
      {hint && <p className={`mt-1 text-xs ${HINT_TEXT[hintTone]}`}>{hint}</p>}
    </div>
  );
}

/** Format an integer with French thousands separator. */
export function formatInt(n: number | null | undefined): string {
  if (n == null) return '—';
  return new Intl.NumberFormat('fr-FR').format(n);
}

export function formatPercent(p: number | null | undefined): string {
  if (p == null) return '—';
  return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(p)} %`;
}
