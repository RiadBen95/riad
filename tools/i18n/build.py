#!/usr/bin/env python3
"""Assemble les traductions (part*.py) en voyage/src/main/res/raw/translations.tsv."""
import importlib.util, glob, os, sys
here = os.path.dirname(os.path.abspath(__file__))
rows, seen = [], {}
for f in sorted(glob.glob(os.path.join(here, "part*.py"))):
    spec = importlib.util.spec_from_file_location("p", f); m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
    for r in m.ROWS:
        if len(r) != 7: sys.exit(f"{f}: ligne à {len(r)} colonnes : {r[0]}")
        if any("\t" in c or "\n" in c for c in r): sys.exit(f"tabulation interdite : {r[0]}")
        if r[0] in seen: continue
        seen[r[0]] = True; rows.append(r)
out = os.path.join(here, "..", "..", "voyage", "src", "main", "res", "raw", "translations.tsv")
with open(out, "w", encoding="utf-8") as fh:
    fh.write("# fr\ten\tar\tes\tzh\tja\thi — généré par tools/i18n/build.py\n")
    for r in rows: fh.write("\t".join(r) + "\n")
print(len(rows), "textes traduits")
