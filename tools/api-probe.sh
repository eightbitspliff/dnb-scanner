#!/usr/bin/env bash
# Diagnose: zeigt echte Antworten möglicher Quellen (läuft in GitHub Actions).
set -u
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36'

echo "===== Beatport Genre-Liste"
curl -s -A "$UA" -H 'Accept-Language: en-US' 'https://www.beatport.com/genre/drum-bass/1/releases?per_page=50' -o /tmp/bp.html -w 'HTTP %{http_code} size %{size_download}\n'
python3 - <<'PY'
import re, json
s = open('/tmp/bp.html', encoding='utf-8', errors='ignore').read()
m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', s, re.S)
if not m:
    print("kein __NEXT_DATA__"); print(s[:600]); raise SystemExit
data = json.loads(m.group(1))
def walk(o, path=""):
    if isinstance(o, dict):
        for k, v in o.items():
            yield from walk(v, path + "/" + k)
    elif isinstance(o, list):
        if o and isinstance(o[0], dict) and "release_date" in json.dumps(o[0])[:4000] and ("artists" in o[0] or "name" in o[0]):
            yield path, o
        for i, v in enumerate(o[:3]):
            yield from walk(v, path + f"[{i}]")
seen = set()
for path, arr in walk(data):
    if path in seen: continue
    seen.add(path)
    print("LISTE", path, "len", len(arr))
    print(json.dumps(arr[0], ensure_ascii=False)[:2500])
    for r in arr[:15]:
        artists = ", ".join(a.get("name","") for a in r.get("artists", []) if isinstance(a, dict))
        g = r.get("genre") or {}
        sg = r.get("sub_genre") or {}
        print("  ", r.get("new_release_date") or r.get("publish_date") or r.get("release_date"), "|", artists, "|", r.get("name"),
              "|", (r.get("label") or {}).get("name"), "| genre:", g.get("name") if isinstance(g, dict) else g,
              "| tracks:", r.get("track_count"))
    break
for key in ["genres", "subGenres", "sub_genres"]:
    for x in re.finditer(r'"name":"([^"]*(?:Jungle|Drum|Liquid|Halftime|Jump)[^"]*)","[^"]*"[^}]{0,200}', m.group(1)):
        print("GENRE-ERWÄHNUNG:", x.group(0)[:250])
    break
PY

echo "===== Beatport Jungle (Subgenre-Suche)"
for u in 'https://www.beatport.com/sub-genre/jungle/137/releases' 'https://www.beatport.com/genre/drum-bass/1/releases?sub_genre=137'; do
  curl -s -A "$UA" "$u" -o /dev/null -w "$u -> HTTP %{http_code}\n"
done

echo "===== Bandcamp discover Seite (eingebettete Daten?)"
curl -s -A "$UA" 'https://bandcamp.com/discover/drum-and-bass?s=new' -o /tmp/bcd.html -w 'HTTP %{http_code} size %{size_download}\n'
grep -o '_fs-ch[^"]\{0,40\}' /tmp/bcd.html | head -2
grep -o 'data-blob' /tmp/bcd.html | head -2

echo "===== Juno Download DnB"
curl -s -A "$UA" 'https://www.junodownload.com/drumandbass/eight-weeks/releases/' -o /tmp/juno.html -w 'HTTP %{http_code} size %{size_download}\n'
grep -o 'class="juno-title"[^<]*<[^>]*>[^<]*' /tmp/juno.html | head -5
