#!/usr/bin/env bash
# Diagnose: zeigt echte Antworten der Quellen-APIs (läuft in GitHub Actions).
set -u
UA='Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36'

bc() { # $1 = JSON-Array der Tags, $2 = category_id
  curl -s -A "$UA" -H 'Content-Type: application/json' -H 'Origin: https://bandcamp.com' -H 'Referer: https://bandcamp.com/discover' \
    -X POST https://bandcamp.com/api/discover/1/discover_web \
    -d "{\"category_id\":${2:-0},\"tag_norm_names\":$1,\"geoname_id\":0,\"slice\":\"new\",\"cursor\":\"*\",\"size\":60,\"include_result_types\":[\"a\",\"s\"]}"
}

for t in '["drum-and-bass"]' '["drum-bass"]' '["jungle"]' '["dnb"]' '["zzz-kein-tag-xyz"]'; do
  echo "===== Bandcamp discover tags=$t"
  bc "$t" > /tmp/bc.json
  jq -r '"result_count=\(.result_count) batch=\(.batch_result_count) keys=\(keys)"' /tmp/bc.json 2>/dev/null || head -c 400 /tmp/bc.json
  jq -r '.results[0:8][] | "  \(.release_date) | \(.band_name) | \(.album_artist) | \(.title) | genre_id=\(.band_genre_id)"' /tmp/bc.json 2>/dev/null
done
echo "===== Bandcamp erstes Ergebnis komplett (drum-and-bass)"
bc '["drum-and-bass"]' | jq '.results[0]' 2>/dev/null | head -80

echo "===== Bandcamp discover-Seite: Genre/Tag-Definitionen mit drum/jungle"
curl -s -A "$UA" https://bandcamp.com/discover/drum-and-bass -o /tmp/disc.html -w 'HTTP %{http_code}\n'
python3 - <<'PY'
import re, html
s = open('/tmp/disc.html', encoding='utf-8', errors='ignore').read()
m = re.search(r'data-blob="([^"]+)"', s) or re.search(r'id="pagedata"[^>]*data-blob="([^"]+)"', s)
if not m:
    print("kein data-blob; Ausschnitte:")
    for x in re.finditer(r'.{120}(drum|jungle).{120}', s, re.I):
        print(x.group(0)[:260]); 
    raise SystemExit
blob = html.unescape(m.group(1))
for x in re.finditer(r'.{0,200}(drum|jungle).{0,200}', blob, re.I):
    print(x.group(0)[:400]); print('---')
PY

echo "===== MusicBrainz"
Q='firstreleasedate:[2026-09-21 TO 2026-10-21] AND (tag:"drum and bass" OR tag:"jungle" OR tag:"dnb" OR tag:"liquid funk" OR tag:"neurofunk")'
curl -s -A 'DnBScanner/0.1 ( https://github.com/eightbitspliff/dnb-scanner )' -G 'https://musicbrainz.org/ws/2/release-group/' \
  --data-urlencode "query=$Q" --data-urlencode fmt=json --data-urlencode limit=25 > /tmp/mb.json
jq -r '"count=\(.count)"' /tmp/mb.json
jq -r '.["release-groups"][] | "  \(."first-release-date") | \(.["artist-credit"][0].name) | \(.title) | tags=\([.tags[]?.name] | join(","))"' /tmp/mb.json
