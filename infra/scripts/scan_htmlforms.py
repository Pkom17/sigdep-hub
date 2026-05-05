#!/usr/bin/env python3
"""
Scan ~/dev/sigdep3-cs/htmlforms/*.html, extract every <obs> reference (and a
few related tags), resolve each concept_id against the live OpenMRS MySQL
to fetch the canonical UUID + name + datatype, and write the consolidated
result to a CSV.

Designed to be re-run after any form change without manual book-keeping.

Usage:
    python3 scan_htmlforms.py [forms_dir] [out_csv]

Defaults:
    forms_dir = ../../../htmlforms
    out_csv   = ../../../concepts_inventory.csv
"""

from __future__ import annotations

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

import pymysql

# --- Config ----------------------------------------------------------------
DB_HOST = "localhost"
DB_PORT = 3305
DB_USER = "sigdep_reader"
DB_PASSWORD = "sigdep"
DB_NAME = "openmrs"

# Tags that carry a concept reference in HTMLFormEntry
# obs / obsgroup / answer / encounterType / etc.
CONCEPT_RE = re.compile(r'(obs|obsgroup|answer|enrollInProgram|workflow)\s+[^>]*?conceptId\s*=\s*"([^"]+)"', re.IGNORECASE)
# Label often sits next to obs as labelText="..." or labelNameTag="..."
LABEL_RE = re.compile(r'labelText\s*=\s*"([^"]*)"', re.IGNORECASE)
# Section tag we walk back to find an enclosing context
SECTION_OPEN_RE = re.compile(r'<section\s+[^>]*?headerLabel\s*=\s*"([^"]*)"', re.IGNORECASE)
SECTION_CLOSE_RE = re.compile(r'</section>', re.IGNORECASE)


def parse_form(path: Path) -> list[dict]:
    """Return a list of references found in this form. Each entry is:
       {tag, concept_id, label, section, line}.
    """
    text = path.read_text(encoding="utf-8", errors="replace")
    refs: list[dict] = []
    section_stack: list[str] = []

    # Walk line by line so we can track the enclosing section as a side-effect.
    for line_num, line in enumerate(text.splitlines(), start=1):
        for m in SECTION_OPEN_RE.finditer(line):
            section_stack.append(m.group(1))
        for m in CONCEPT_RE.finditer(line):
            tag = m.group(1)
            concept_id = m.group(2)
            label_match = LABEL_RE.search(line)
            label = label_match.group(1) if label_match else ""
            refs.append({
                "form": path.stem,
                "line": line_num,
                "tag": tag,
                "concept_id": concept_id,
                "label": label.strip(),
                "section": " > ".join(section_stack) if section_stack else "",
            })
        for _ in SECTION_CLOSE_RE.finditer(line):
            if section_stack:
                section_stack.pop()
    return refs


def resolve_concepts(concept_ids: set[str]) -> dict[str, dict]:
    """Look up each concept_id in OpenMRS. Returns concept_id -> {uuid, name, dt}.

    Locale priority: French fully-specified > French short > English fully-specified
    > English short > anything else. Avoids picking the Swahili / Spanish / Creole
    translations that happen to be marked locale_preferred in some OpenMRS exports.
    """
    if not concept_ids:
        return {}
    print(f"Resolving {len(concept_ids)} distinct concepts against MySQL ...")
    conn = pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASSWORD,
        db=DB_NAME, charset="utf8mb4",
    )
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            placeholders = ",".join(["%s"] * len(concept_ids))
            # Pull the row where the locale ranking is best.
            cur.execute(
                f"""
                SELECT c.concept_id,
                       c.uuid,
                       (
                         SELECT cn.name FROM concept_name cn
                         WHERE cn.concept_id = c.concept_id AND cn.voided = 0
                         ORDER BY
                           CASE
                             WHEN cn.locale = 'fr' AND cn.concept_name_type = 'FULLY_SPECIFIED' THEN 1
                             WHEN cn.locale = 'fr' THEN 2
                             WHEN cn.locale = 'en' AND cn.concept_name_type = 'FULLY_SPECIFIED' THEN 3
                             WHEN cn.locale = 'en' THEN 4
                             ELSE 5
                           END,
                           CASE WHEN cn.locale_preferred = 1 THEN 0 ELSE 1 END,
                           cn.concept_name_id
                         LIMIT 1
                       ) AS name,
                       cdt.hl7_abbreviation AS dt
                FROM concept c
                LEFT JOIN concept_datatype cdt
                       ON cdt.concept_datatype_id = c.datatype_id
                WHERE c.concept_id IN ({placeholders})
                """,
                tuple(concept_ids),
            )
            rows = cur.fetchall()
    finally:
        conn.close()
    return {str(r["concept_id"]): r for r in rows}


def main() -> int:
    here = Path(__file__).resolve().parent
    default_dir = here.parent.parent.parent / "htmlforms"
    default_csv = here.parent.parent.parent / "concepts_inventory.csv"

    forms_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else default_dir
    out_csv = Path(sys.argv[2]) if len(sys.argv) > 2 else default_csv

    if not forms_dir.is_dir():
        print(f"ERROR: forms dir not found: {forms_dir}", file=sys.stderr)
        return 1

    all_refs: list[dict] = []
    for f in sorted(forms_dir.glob("*.html")):
        refs = parse_form(f)
        print(f"  {f.name}: {len(refs)} concept references")
        all_refs.extend(refs)

    if not all_refs:
        print("No <obs> references found.")
        return 0

    distinct_ids = {r["concept_id"] for r in all_refs}
    resolved = resolve_concepts(distinct_ids)

    # Build a per-concept summary: concept_id -> {uuid, name, dt, forms{}}
    by_concept: dict[str, dict] = {}
    for r in all_refs:
        cid = r["concept_id"]
        info = by_concept.setdefault(cid, {
            "concept_id": cid,
            "uuid": (resolved.get(cid) or {}).get("uuid", ""),
            "name": (resolved.get(cid) or {}).get("name", ""),
            "dt":   (resolved.get(cid) or {}).get("dt", ""),
            "forms": defaultdict(list),
        })
        info["forms"][r["form"]].append({
            "label": r["label"],
            "section": r["section"],
            "tag": r["tag"],
            "line": r["line"],
        })

    # Write CSV: one row per (concept, form) so the same concept appearing in
    # 3 forms produces 3 rows, easy to filter in Excel.
    with out_csv.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow([
            "concept_id", "uuid", "name", "datatype",
            "form", "section", "label", "tag", "line",
            "forms_count",
        ])
        for cid, info in sorted(by_concept.items(), key=lambda kv: int(kv[0]) if kv[0].isdigit() else 0):
            forms_count = len(info["forms"])
            for form, occurrences in info["forms"].items():
                # one row per form, but if a concept appears N times in the same
                # form we keep the first label/section to keep it tidy.
                first = occurrences[0]
                w.writerow([
                    cid, info["uuid"], info["name"], info["dt"],
                    form, first["section"], first["label"], first["tag"], first["line"],
                    forms_count,
                ])

    # Stats summary
    print(f"\n=== Summary ===")
    print(f"Total distinct concepts:      {len(by_concept)}")
    unresolved = [cid for cid, info in by_concept.items() if not info["uuid"]]
    print(f"Unresolved (not in MySQL):    {len(unresolved)}")
    if unresolved:
        print(f"  -> {unresolved[:10]}{'...' if len(unresolved) > 10 else ''}")

    # Concepts shared across multiple forms
    shared = sorted(
        ((cid, info) for cid, info in by_concept.items() if len(info["forms"]) > 1),
        key=lambda kv: -len(kv[1]["forms"])
    )
    print(f"Concepts in 2+ forms:         {len(shared)}")
    for cid, info in shared[:10]:
        forms_str = ", ".join(info["forms"].keys())
        print(f"  {cid:>8}  {info['name'][:40]:<40}  in [{forms_str}]")

    print(f"\nCSV written -> {out_csv}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
