#!/usr/bin/env bash
#
# Downloads Material Symbols (outlined, 24px) VectorDrawables from Google's
# repo into app/src/main/res/drawable/, normalised to this project's house style.
#
# This script also regenerates TileIcons.kt, so that map can never drift from what is on disk.
#
# Usage: tools/fetch-event-icons.sh
#
set -euo pipefail

BASE_URL="https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Event glyphs live in their own resource root
CHROME_DIR="$REPO_ROOT/app/src/main/res/drawable"
DRAWABLE_DIR="$REPO_ROOT/app/src/main/res-event-icons/drawable"
TILE_ICONS_KT="$REPO_ROOT/app/src/main/java/nl/agroqirax/calendartile/TileIcons.kt"

# Icons used as UI chrome rather than event glyphs. Not part of the event map.
CHROME_ICONS=(
    add
    arrow_back
    calendar_today
    chevron_right
    delete
    match_case
    match_word
    regular_expression
    science
    today
    widgets
)

# Event glyphs, keyed by EventIconMapper's icon names (which are the official
# Material Symbols names). Keep this list sorted for readable diffs.
EVENT_ICONS=(
    beach_access
    cake
    call
    celebration
    child_care
    church
    cleaning_services
    content_cut
    dentistry
    directions_car
    fitness_center
    flight
    grocery
    groups
    hotel
    local_bar
    local_cafe
    local_shipping
    medical_services
    menu_book
    movie
    music_note
    pets
    recycling
    restaurant
    rocket_launch
    savings
    school
    shopping_cart
    sports_motorsports
    sports_soccer
    train
    videocam
    work
)

# Icons whose materialsymbolsrounded/*_fill1_*.xml asset in the upstream
# GitHub repo is stale: byte-identical to the outline at every weight, so it
# never actually renders filled. fonts.google.com renders these correctly
# because it serves from fonts.gstatic.com instead, which has the real glyph.
# For these, fetch the gstatic SVG and convert it rather than the GitHub XML.
GSTATIC_FILL_OVERRIDES=(
    grocery
)

GSTATIC_BASE="https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded"

force=false
while getopts "f" opt; do
    case "$opt" in
        f) force=true ;;
        *) exit 1 ;;
    esac
done

is_gstatic_override() {
    local name="$1"
    for n in "${GSTATIC_FILL_OVERRIDES[@]}"; do
        [ "$n" = "$name" ] && return 0
    done
    return 1
}

# gstatic SVGs use viewBox "0 -960 960 960"; the project's vector drawables
# use viewportWidth/Height "960 960" with the same glyph shifted to start at
# y=0. Shifting is just "add 960 to every absolute y", since translation
# doesn't affect the deltas in relative (lowercase) commands.
svg_path_to_vector_pathdata() {
    python3 - "$1" <<'PY'
import re, sys
d = sys.argv[1]
tokens = re.findall(r'[MLHVCSQTAZmlhvcsqtaz]|-?\d+\.?\d*', d)
params_count = {'M':2,'L':2,'H':1,'V':1,'C':6,'S':4,'Q':4,'T':2,'A':7}
out, i, cur_cmd = [], 0, None
while i < len(tokens):
    tok = tokens[i]
    if re.match(r'^[MLHVCSQTAZmlhvcsqtaz]$', tok):
        cur_cmd = tok
        out.append(tok)
        i += 1
        continue
    letter = cur_cmd.upper()
    n = params_count[letter]
    nums = [float(tokens[i + k]) for k in range(n)]
    i += n
    if cur_cmd.isupper():
        if letter == 'V':
            nums[0] += 960
        elif letter != 'H':
            for k in range(1, len(nums), 2):
                nums[k] += 960

    def fmt(x):
        return str(int(x)) if x == int(x) else str(x)

    out.append(','.join(fmt(x) for x in nums))
print(''.join(out))
PY
}

fetch_icon_from_gstatic() {
    local name="$1"
    local out="$2"
    local url="$GSTATIC_BASE/$name/fill1/24px.svg"

    local svg
    if ! svg="$(curl -sf "$url")"; then
        echo "  FAILED: $name (not found at $url)" >&2
        return 1
    fi
    local raw_path
    raw_path="$(echo "$svg" | grep -o 'd="[^"]*"' | sed 's/^d="//;s/"$//')"
    if [ -z "$raw_path" ]; then
        echo "  FAILED: $name (no path data in $url)" >&2
        return 1
    fi
    local path_data
    path_data="$(svg_path_to_vector_pathdata "$raw_path")"

    cat > "$out" <<EOF
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
  <path
      android:fillColor="#e3e3e3"
      android:pathData="$path_data"/>
</vector>
EOF

    if ! xmllint --noout "$out" 2>/dev/null; then
        rm -f "$out"
        echo "  FAILED: $name (malformed XML after conversion)" >&2
        return 1
    fi
    echo "  ic_${name}.xml (from gstatic override)"
}

fetch_icon() {
    local name="$1"
    local out_dir="$2"
    local url="$BASE_URL/$name/materialsymbolsrounded/${name}_fill1_24px.xml"
    local out="$out_dir/ic_${name}.xml"

    if [ -f "$out" ] && [ "$force" = false ]; then
        echo "  ic_${name}.xml (already exists, skipped)"
        return 0
    fi

    if is_gstatic_override "$name"; then
        fetch_icon_from_gstatic "$name" "$out"
        return $?
    fi

    # Normalisations, both load-bearing:
    #  - Drop android:tint="?attr/colorControlNormal". SystemUI loads our drawable
    #    through a package context whose theme has no colorControlNormal, and it
    #    tints the tile icon itself anyway.
    #  - Use the same literal fill as the rest of the project's drawables.
    #
    # The tint is removed together with its preceding newline and indent, never
    # as a whole line: it is the last attribute in most of these files, so its
    # line carries the closing '>' of the <vector> tag. Deleting the line drops
    # that '>' and silently produces malformed XML. A few icons (autoMirrored
    # ones) put another attribute after the tint, so both shapes must work.
    if ! curl -sf "$url" \
        | perl -0pe 's/\n\s*android:tint="\?attr\/colorControlNormal"//g' \
        | sed 's|@android:color/white|#e3e3e3|' \
        > "$out"; then
        rm -f "$out"
        echo "  FAILED: $name (not found at $url)" >&2
        return 1
    fi

    # curl -f catches HTTP errors, but not a truncated body or a normalisation
    # that mangled the markup — so parse what we actually wrote.
    if ! xmllint --noout "$out" 2>/dev/null; then
        rm -f "$out"
        echo "  FAILED: $name (malformed XML after normalisation)" >&2
        return 1
    fi

    echo "  ic_${name}.xml"
}

generate_tile_icons_kt() {
    {
        echo "package nl.agroqirax.calendartile"
        echo
        echo "/**"
        echo " * The event icons, and the drawable each one resolves to."
        echo " *"
        echo " * Generated by tools/fetch-event-icons.sh — do not edit by hand."
        echo " */"
        echo "object TileIcons {"
        echo
        for name in "${EVENT_ICONS[@]}"; do
            echo "    const val ${name^^} = \"$name\""
        done
        echo
        echo "    /** Every icon, in a stable order, for the custom-mapping picker. */"
        echo "    val all: Map<String, Int> = linkedMapOf("
        for name in "${EVENT_ICONS[@]}"; do
            echo "        ${name^^} to R.drawable.ic_$name,"
        done
        echo "    )"
        echo
        echo "    /** Returns the drawable for [name], or null if there is no icon for it. */"
        echo "    fun resIdFor(name: String): Int? = all[name]"
        echo "}"
    } > "$TILE_ICONS_KT"
    tools/ktfmt.sh ${TILE_ICONS_KT#"$REPO_ROOT"/}
    echo "Generated ${TILE_ICONS_KT#"$REPO_ROOT"/} (${#EVENT_ICONS[@]} icons)"
}

mkdir -p "$DRAWABLE_DIR" "$CHROME_DIR"
failed=0

echo "Chrome icons -> ${CHROME_DIR#"$REPO_ROOT"/}"
for name in "${CHROME_ICONS[@]}"; do
    fetch_icon "$name" "$CHROME_DIR" || failed=$((failed + 1))
done

echo "Event icons -> ${DRAWABLE_DIR#"$REPO_ROOT"/}"
for name in "${EVENT_ICONS[@]}"; do
    fetch_icon "$name" "$DRAWABLE_DIR" || failed=$((failed + 1))
done

if [ "$failed" -gt 0 ]; then
    echo
    echo "$failed icon(s) failed — fix the names above before regenerating TileIcons.kt." >&2
    exit 1
fi

echo
generate_tile_icons_kt
