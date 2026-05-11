import type { ReactNode } from 'react';

/**
 * Compact status pill with a leading dot. Use the same `tone` across pages
 * so the meaning of each colour stays consistent:
 *
 *   ok       — green   (active, healthy, suppressed VL, recent sync)
 *   warning  — amber   (partial, late, attention needed)
 *   danger   — rose    (failed, offline, rejected)
 *   info     — sky     (informational / scoped — SIGDEP, etc.)
 *   neutral  — slate   (no data, "inconnu")
 */
export type BadgeTone = 'ok' | 'warning' | 'danger' | 'info' | 'neutral';

const TONES: Record<BadgeTone, { wrap: string; dot: string }> = {
  ok:      { wrap: 'bg-emerald-50 text-emerald-700 ring-emerald-200', dot: 'bg-emerald-500' },
  warning: { wrap: 'bg-amber-50   text-amber-800   ring-amber-200',   dot: 'bg-amber-500'   },
  danger:  { wrap: 'bg-rose-50    text-rose-700    ring-rose-200',    dot: 'bg-rose-500'    },
  info:    { wrap: 'bg-sigdep-50  text-sigdep-700  ring-sigdep-200',  dot: 'bg-sigdep-500'  },
  neutral: { wrap: 'bg-slate-100  text-slate-600   ring-slate-200',   dot: 'bg-slate-400'   },
};

export function StatusBadge({
  tone,
  children,
  dot = true,
}: Readonly<{ tone: BadgeTone; children: ReactNode; dot?: boolean }>) {
  const t = TONES[tone];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5
                      text-xs font-medium ring-1 ring-inset ${t.wrap}`}>
      {dot && <span aria-hidden="true" className={`h-1.5 w-1.5 rounded-full ${t.dot}`} />}
      {children}
    </span>
  );
}
