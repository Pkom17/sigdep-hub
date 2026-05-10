package ci.itechciv.sigdep.hub.domain.service;

import java.util.Map;

/**
 * Resolves a sort key + direction from controller params into a safe ORDER BY
 * fragment. Each listing service supplies its own whitelist mapping logical
 * column names (the keys the frontend uses) to SQL expressions.
 *
 *   SortSpec.orderBy(sort, dir, Map.of(
 *       "date",   "v.visit_date",
 *       "site",   "site.code"
 *   ), "v.visit_date DESC NULLS LAST, v.id DESC");
 *
 * If `sort` isn't in the whitelist, the supplied default fragment is used —
 * keeping the original ORDER BY for unknown / missing params.
 */
final class SortSpec {

    private SortSpec() {}

    static String orderBy(String sort, String dir,
                          Map<String, String> whitelist,
                          String defaultFragment) {
        if (sort == null || sort.isBlank()) return " ORDER BY " + defaultFragment;
        String expr = whitelist.get(sort);
        if (expr == null) return " ORDER BY " + defaultFragment;
        boolean desc = "desc".equalsIgnoreCase(dir);
        // NULLS LAST keeps the listing predictable when sorting on optional columns.
        return " ORDER BY " + expr + (desc ? " DESC NULLS LAST" : " ASC NULLS LAST");
    }
}
