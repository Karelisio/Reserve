#!/usr/bin/env bash
# Checks that the public APIs used by the app still answer in the format it expects.
# Usage: check-api.sh [postal code]   (default 38270)
set -euo pipefail
CP="${1:-38270}"
DATASET="https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2"
FIELDS="id,geom,adresse,ville,cp,gazole_prix,gazole_maj,e10_prix,e10_maj,sp95_prix,sp95_maj,sp98_prix,sp98_maj,carburants_rupture_definitive"
fail=0
check() { if eval "$2"; then echo "OK   $1"; else echo "FAIL $1"; fail=1; fi; }

export_json=$(curl -sSf -G "$DATASET/exports/json" --data-urlencode "where=cp=\"$CP\"" --data-urlencode "select=$FIELDS" --data-urlencode "limit=-1")
check "export : tableau JSON" '[ "$(jq -r type <<<"$export_json")" = array ]'
check "export : au moins une station" '[ "$(jq length <<<"$export_json")" -gt 0 ]'
check "export : geom {lat, lon}" 'jq -e "all(.[]; (.geom.lat|type)==\"number\" and (.geom.lon|type)==\"number\")" <<<"$export_json" >/dev/null'
check "export : prix en euros" 'jq -e "[.[] | .gazole_prix, .e10_prix, .sp98_prix | numbers] | all(. > 0.3 and . < 5)" <<<"$export_json" >/dev/null'

records=$(curl -sSf -G "$DATASET/records" --data-urlencode "where=cp=\"$CP\"" --data-urlencode "select=$FIELDS" --data-urlencode "limit=100")
check "records : même nombre que l'export" '[ "$(jq .total_count <<<"$records")" = "$(jq length <<<"$export_json")" ]'

near=$(curl -sSf -G "$DATASET/exports/json" --data-urlencode "where=within_distance(geom, geom'POINT(5.03 45.36)', 10000m)" --data-urlencode "select=id" --data-urlencode "limit=-1")
check "within_distance accepté" '[ "$(jq length <<<"$near")" -gt 0 ]'

check "API Adresse" 'curl -sSf "https://api-adresse.data.gouv.fr/search/?q=Primarette&limit=1" | jq -e ".features | length > 0" >/dev/null'
check "Overpass" 'curl -sSf --max-time 60 https://overpass-api.de/api/interpreter --data-urlencode "data=[out:json][timeout:25];nwr[\"amenity\"=\"fuel\"](45.33,5.00,45.40,5.08);out center tags;" | jq -e ".elements | length > 0" >/dev/null'

echo
echo "Stations du $CP :"
jq -r '.[] | "\(.id)  \(.ville)  \(.adresse)  gazole=\(.gazole_prix) e10=\(.e10_prix) sp95=\(.sp95_prix) sp98=\(.sp98_prix)"' <<<"$export_json"
exit $fail
