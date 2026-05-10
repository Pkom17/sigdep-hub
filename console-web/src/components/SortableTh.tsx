import { ReactNode } from 'react';

export type SortState = { key: string; dir: 'asc' | 'desc' } | null;

/**
 * Header cell that toggles a sort key/dir on click. The parent owns the
 * SortState and passes it down so the indicator always matches the active
 * sort.
 *
 * Click cycles: nothing → asc → desc → nothing.
 */
export function SortableTh({
  k,
  sort,
  onSort,
  align,
  children,
}: Readonly<{
  k: string;
  sort: SortState;
  onSort: (next: SortState) => void;
  align?: 'left' | 'right';
  children: ReactNode;
}>) {
  const active = sort?.key === k ? sort.dir : null;
  const indicator = active === 'asc' ? '▲' : active === 'desc' ? '▼' : '';

  function next(): SortState {
    if (active === null) return { key: k, dir: 'asc' };
    if (active === 'asc') return { key: k, dir: 'desc' };
    return null;
  }

  const justify = align === 'right' ? 'justify-end' : 'justify-start';

  return (
    <th className={`px-4 py-2 font-medium ${align === 'right' ? 'text-right' : 'text-left'}`}>
      <button
        type="button"
        onClick={() => onSort(next())}
        className={`flex items-center gap-1 ${justify} w-full hover:text-sigdep-700`}
      >
        <span>{children}</span>
        <span
          className={`text-[10px] ${active ? 'text-sigdep-600' : 'text-slate-400'}`}
        >
          {indicator || '⇅'}
        </span>
      </button>
    </th>
  );
}
