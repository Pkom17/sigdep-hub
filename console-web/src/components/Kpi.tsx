type Props = {
  label: string;
  value: string;
  hint?: string;
  hintTone?: 'positive' | 'neutral' | 'warning';
};

const tones = {
  positive: 'text-emerald-600',
  neutral:  'text-slate-500',
  warning:  'text-amber-600',
};

export function Kpi({ label, value, hint, hintTone = 'neutral' }: Props) {
  return (
    <div className="card p-4">
      <p className="text-xs font-medium text-ink-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      {hint && <p className={`mt-1 text-xs ${tones[hintTone]}`}>{hint}</p>}
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
