#!/usr/bin/env python3
"""Assemble les traductions (en.py) en voyage/src/main/res/raw/translations.tsv (fr, en)."""
import importlib.util, os, sys
here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("en", os.path.join(here, "en.py")); m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
rows, seen = [], set()
for r in m.ROWS:
    if len(r) != 2 or not all(r): sys.exit(f"ligne invalide : {r}")
    if any("\t" in c or "\n" in c for c in r): sys.exit(f"tabulation interdite : {r[0]}")
    if r[0] not in seen: seen.add(r[0]); rows.append(r)
out = os.path.join(here, "..", "..", "voyage", "src", "main", "res", "raw", "translations.tsv")
with open(out, "w", encoding="utf-8") as fh:
    fh.write("# fr\ten — généré par tools/i18n/build.py\n")
    for r in rows: fh.write("\t".join(r) + "\n")
print(len(rows), "textes traduits")
