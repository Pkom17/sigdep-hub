import { useQuery } from '@tanstack/react-query';
import { fetchDistricts, fetchRegions, fetchSitesOf } from '../api/client';

export type GeoScope = {
  regionId?: number;
  districtId?: number;
  siteId?: number;
};

/**
 * Three cascading <select>s — region → district → site. Empty option means
 * "all" at that level. Choosing a parent resets the children.
 *
 * The component is uncontrolled-style: it lifts the scope through the
 * `value` + `onChange` props, so pages can plug it in their useState/
 * useQuery flow without copy/pasting the cascade logic.
 */
export function GeoFilter({
  value,
  onChange,
}: Readonly<{ value: GeoScope; onChange: (next: GeoScope) => void }>) {
  const regions = useQuery({ queryKey: ['regions'], queryFn: fetchRegions });

  const districts = useQuery({
    queryKey: ['districts', value.regionId],
    queryFn: () => fetchDistricts(value.regionId),
    enabled: value.regionId != null,
  });

  // Sites need either a region or a district to avoid loading the full 3,880.
  const sites = useQuery({
    queryKey: ['sitesOf', value.regionId, value.districtId],
    queryFn: () => fetchSitesOf(value.regionId, value.districtId),
    enabled: value.regionId != null || value.districtId != null,
  });

  // Fixed widths kill the shift when an empty disabled select fills with
  // its first selection; otherwise <select> width = widest option.
  const selectBase =
    'rounded-md border border-slate-300 px-3 py-2 text-sm bg-white disabled:bg-slate-100 disabled:text-ink-subtle';

  return (
    <>
      <select
        className={`${selectBase} w-44`}
        value={value.regionId ?? ''}
        onChange={(e) => {
          const v = e.target.value ? Number(e.target.value) : undefined;
          onChange({ regionId: v });
        }}
      >
        <option value="">Toutes les régions</option>
        {regions.data?.map((r) => (
          <option key={r.id} value={r.id}>{r.name}</option>
        ))}
      </select>

      <select
        className={`${selectBase} w-52`}
        value={value.districtId ?? ''}
        disabled={value.regionId == null}
        onChange={(e) => {
          const v = e.target.value ? Number(e.target.value) : undefined;
          onChange({ regionId: value.regionId, districtId: v });
        }}
      >
        <option value="">
          {value.regionId == null ? 'District (région d’abord)' : 'Tous les districts'}
        </option>
        {districts.data?.map((d) => (
          <option key={d.id} value={d.id}>{d.name}</option>
        ))}
      </select>

      <select
        className={`${selectBase} w-64`}
        value={value.siteId ?? ''}
        disabled={value.regionId == null && value.districtId == null}
        onChange={(e) => {
          const v = e.target.value ? Number(e.target.value) : undefined;
          onChange({
            regionId: value.regionId,
            districtId: value.districtId,
            siteId: v,
          });
        }}
      >
        <option value="">
          {value.regionId == null && value.districtId == null
            ? 'Site (région ou district d’abord)'
            : 'Tous les sites'}
        </option>
        {sites.data?.map((s) => (
          <option key={s.id} value={s.id}>
            {s.code} — {s.name}
          </option>
        ))}
      </select>
    </>
  );
}
