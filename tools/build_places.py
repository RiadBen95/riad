#!/usr/bin/env python3
"""
Génère la base des lieux remarquables du monde (musées, monuments, attractions, parcs, plages)
à partir de Wikidata, découpée en zones de 2° × 2°, publiée en ligne (data/places/) et
téléchargée par l'application zone par zone : la recherche est quasi instantanée.

Usage : python tools/build_places.py data/places
"""
import json
import math
import os
import sys
import time
import urllib.parse
import urllib.request

ENDPOINT = "https://query.wikidata.org/sparql"
UA = "EnvoleeDataBuilder/1.0 (https://github.com/RiadBen95/riad)"

CATEGORIES = ["MUSEUMS", "MONUMENTS", "ATTRACTIONS", "PARKS", "BEACHES"]

# (catégorie, classe Wikidata, libellé affiché, nombre minimum de Wikipédias)
CLASSES = [
    ("MUSEUMS", "Q33506", "Musée", 4),
    ("MUSEUMS", "Q207694", "Musée d'art", 3),
    ("MUSEUMS", "Q17431399", "Musée national", 3),
    ("MUSEUMS", "Q1007870", "Galerie d'art", 4),
    ("MUSEUMS", "Q2772772", "Musée militaire", 3),
    ("MUSEUMS", "Q588140", "Musée des sciences", 3),
    ("MUSEUMS", "Q16735822", "Musée d'histoire", 3),
    ("MUSEUMS", "Q756102", "Écomusée", 3),
    ("MUSEUMS", "Q2087181", "Maison-musée", 3),
    ("MONUMENTS", "Q23413", "Château", 5),
    ("MONUMENTS", "Q16560", "Palais", 5),
    ("MONUMENTS", "Q2977", "Cathédrale", 5),
    ("MONUMENTS", "Q163687", "Basilique", 5),
    ("MONUMENTS", "Q120560", "Basilique", 6),
    ("MONUMENTS", "Q32815", "Mosquée", 6),
    ("MONUMENTS", "Q34627", "Synagogue", 6),
    ("MONUMENTS", "Q44539", "Temple", 6),
    ("MONUMENTS", "Q44613", "Monastère", 6),
    ("MONUMENTS", "Q160742", "Abbaye", 6),
    ("MONUMENTS", "Q16970", "Église", 12),
    ("MONUMENTS", "Q4989906", "Monument", 5),
    ("MONUMENTS", "Q5003624", "Mémorial", 6),
    ("MONUMENTS", "Q839954", "Site archéologique", 6),
    ("MONUMENTS", "Q57821", "Fortification", 6),
    ("MONUMENTS", "Q1785071", "Fort", 6),
    ("MONUMENTS", "Q12518", "Tour", 6),
    ("MONUMENTS", "Q641226", "Arc de triomphe", 4),
    ("MONUMENTS", "Q12280", "Pont", 15),
    ("MONUMENTS", "Q174782", "Place", 10),
    ("ATTRACTIONS", "Q570116", "Attraction", 4),
    ("ATTRACTIONS", "Q43501", "Zoo", 4),
    ("ATTRACTIONS", "Q194195", "Parc d'attractions", 4),
    ("ATTRACTIONS", "Q2281788", "Aquarium", 4),
    ("ATTRACTIONS", "Q1440300", "Tour d'observation", 5),
    ("ATTRACTIONS", "Q2319498", "Lieu emblématique", 4),
    ("ATTRACTIONS", "Q153562", "Opéra", 6),
    ("ATTRACTIONS", "Q24354", "Théâtre", 8),
    ("ATTRACTIONS", "Q1060829", "Salle de concert", 8),
    ("ATTRACTIONS", "Q483110", "Stade", 15),
    ("PARKS", "Q22698", "Parc", 4),
    ("PARKS", "Q22746", "Parc urbain", 3),
    ("PARKS", "Q167346", "Jardin botanique", 3),
    ("PARKS", "Q1107656", "Jardin", 4),
    ("PARKS", "Q46169", "Parc national", 8),
    ("BEACHES", "Q40080", "Plage", 2),
]

QUERY = """
SELECT ?item ?coord ?sl ?fr ?en ?img ?frwiki ?enwiki WHERE {
  ?item wdt:P31 wd:%(cls)s ; wikibase:sitelinks ?sl ; wdt:P625 ?coord .
  FILTER(?sl >= %(min)d)
  OPTIONAL { ?item rdfs:label ?fr FILTER(LANG(?fr) = "fr") }
  OPTIONAL { ?item rdfs:label ?en FILTER(LANG(?en) = "en") }
  OPTIONAL { ?item wdt:P18 ?img }
  OPTIONAL { ?frwiki schema:about ?item ; schema:isPartOf <https://fr.wikipedia.org/> }
  OPTIONAL { ?enwiki schema:about ?item ; schema:isPartOf <https://en.wikipedia.org/> }
}
"""


def run(query):
    data = urllib.parse.urlencode({"query": query, "format": "json"}).encode()
    for attempt in range(5):
        try:
            req = urllib.request.Request(ENDPOINT, data=data, headers={
                "User-Agent": UA,
                "Accept": "application/sparql-results+json",
                "Content-Type": "application/x-www-form-urlencoded",
            })
            with urllib.request.urlopen(req, timeout=180) as r:
                return json.load(r)["results"]["bindings"]
        except Exception as e:  # 429, 5xx, délai dépassé…
            wait = 10 * (attempt + 1)
            print(f"   tentative {attempt + 1} échouée ({e}), nouvel essai dans {wait} s", flush=True)
            time.sleep(wait)
    return None


def val(row, key):
    v = row.get(key)
    return v["value"] if v else None


def wiki_title(url):
    return urllib.parse.unquote(url.rsplit("/wiki/", 1)[1]) if url and "/wiki/" in url else None


def main(out):
    kinds = []
    items = {}
    for cat, cls, label, minimum in CLASSES:
        print(f"{cat:12} {cls:10} {label} (≥ {minimum})", flush=True)
        rows = run(QUERY % {"cls": cls, "min": minimum})
        if rows is None:
            print("   ignoré après plusieurs échecs", flush=True)
            continue
        if label not in kinds:
            kinds.append(label)
        added = 0
        for row in rows:
            qid = val(row, "item").rsplit("/", 1)[-1]
            if qid in items:
                continue  # déjà classé dans une catégorie précédente
            coord = val(row, "coord") or ""
            if not coord.startswith("Point("):
                continue
            try:
                lon, lat = (float(x) for x in coord[6:-1].split())
            except ValueError:
                continue
            name = val(row, "fr") or val(row, "en")
            if not name or name == qid:
                continue
            img = val(row, "img")
            img = urllib.parse.unquote(img.rsplit("/Special:FilePath/", 1)[1]) if img and "/Special:FilePath/" in img else ""
            fr, en = wiki_title(val(row, "frwiki")), wiki_title(val(row, "enwiki"))
            wiki = f"fr:{fr}" if fr else (f"en:{en}" if en else "")
            items[qid] = [name, round(lat, 5), round(lon, 5), int(val(row, "sl")),
                          CATEGORIES.index(cat), kinds.index(label), img, wiki]
            added += 1
        print(f"   {len(rows)} lignes, {added} lieux ajoutés (total {len(items)})", flush=True)
        time.sleep(2)

    if len(items) < 5000:
        sys.exit(f"Trop peu de lieux ({len(items)}) : la génération a sans doute échoué.")
    # Découpage en zones de 2° × 2° (environ 200 km), une par fichier.
    tiles = {}
    for item in items.values():
        key = f"{math.floor(item[1] / 2)}_{math.floor(item[2] / 2)}"
        tiles.setdefault(key, []).append(item)
    os.makedirs(out, exist_ok=True)
    total = 0
    for key, tile_items in tiles.items():
        tile_items.sort(key=lambda i: -i[3])
        payload = {"v": 1, "categories": CATEGORIES, "kinds": kinds, "items": tile_items}
        raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
        total += len(raw)
        with open(os.path.join(out, f"{key}.json"), "wb") as f:
            f.write(raw)
    with open(os.path.join(out, "index.json"), "w") as f:
        json.dump({"v": 1, "places": len(items), "tiles": {k: len(v) for k, v in sorted(tiles.items())}}, f)
    biggest = max(tiles.items(), key=lambda kv: len(kv[1]))
    print(f"{len(items)} lieux dans {len(tiles)} zones, {total // 1024} Ko au total ; "
          f"plus grosse zone {biggest[0]} : {len(biggest[1])} lieux")
    per_cat = {c: sum(1 for i in items.values() if i[4] == n) for n, c in enumerate(CATEGORIES)}
    print("Par catégorie :", per_cat)


if __name__ == "__main__":
    main(sys.argv[1])
