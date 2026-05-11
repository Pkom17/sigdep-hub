import { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';

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

  function next(): SortState {
    if (active === null) return { key: k, dir: 'asc' };
    if (active === 'asc') return { key: k, dir: 'desc' };
    return null;
  }

  const justify = align === 'right' ? 'justify-end' : 'justify-start';
  const Icon = active === 'asc' ? ArrowUp : active === 'desc' ? ArrowDown : ArrowUpDown;

  return (
    <th className={`px-4 py-2 font-medium ${align === 'right' ? 'text-right' : 'text-left'}`}>
      <button
        type="button"
        onClick={() => onSort(next())}
        className={`group flex items-center gap-1.5 ${justify} w-full
                    hover:text-sigdep-700 transition-colors`}
      >
        <span>{children}</span>
        <Icon
          className={`h-3 w-3 transition-opacity
                      ${active ? 'text-sigdep-600 opacity-100'
                              : 'text-slate-400 opacity-40 group-hover:opacity-100'}`}
        />
      </button>
    </th>
  );
}
