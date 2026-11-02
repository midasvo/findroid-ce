#!/usr/bin/env bash
# Generates two short test series with several audio and subtitle tracks, for testing track
# selection in the player. See docs/track-test-series.md.
#
# Usage: scripts/make-track-test-series.sh [output-dir]   (default: ./track-test-series)
set -euo pipefail

command -v ffmpeg >/dev/null || { echo "ffmpeg not found on PATH" >&2; exit 1; }

OUT="${1:-track-test-series}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Writes a subtitle file whose single cue names the episode and track, so the active subtitle track
# can be read off the screen.
srt() { printf '1\n00:00:00,500 --> 00:00:19,500\n%s\n' "$1" > "$TMP/$2.srt"; }

# episode <dir> <file> <label> <audio>... -- <subtitle>...
#   audio:    "language|title|tone frequency in Hz"
#   subtitle: "language|title", an empty language leaves the track untagged
episode() {
    local dir="$1" file="$2" label="$3"
    shift 3
    local args=(-y -loglevel error -f lavfi -i "testsrc=size=320x240:rate=25:duration=20")
    local maps=(-map 0:v) meta=() input=1 audio=0 sub=0 mode=audio spec lang title freq
    for spec in "$@"; do
        if [ "$spec" = "--" ]; then mode=sub; continue; fi
        IFS='|' read -r lang title freq <<< "$spec"
        if [ "$mode" = audio ]; then
            args+=(-f lavfi -i "sine=frequency=$freq:duration=20")
            maps+=(-map "$input:a")
            [ -n "$lang" ] && meta+=("-metadata:s:a:$audio" "language=$lang")
            meta+=("-metadata:s:a:$audio" "title=$title")
            audio=$((audio + 1))
        else
            srt "$label - $title" "s$input"
            args+=(-i "$TMP/s$input.srt")
            maps+=(-map "$input:s")
            meta+=("-metadata:s:s:$sub" "language=${lang:-und}" "-metadata:s:s:$sub" "title=$title")
            sub=$((sub + 1))
        fi
        input=$((input + 1))
    done
    mkdir -p "$OUT/$dir"
    ffmpeg "${args[@]}" "${maps[@]}" -c:v libx264 -preset veryfast -pix_fmt yuv420p \
        -c:a aac -b:a 64k -c:s srt "${meta[@]}" -disposition:a:0 default -disposition:s 0 \
        "$OUT/$dir/$file"
}

SERIES="Track Test (2026)/Season 01"
FULL_AUDIO=("dut|Nederlands|440" "eng|English|660" "eng|English Commentary|880")
FULL_SUBS=("dut|Nederlands" "eng|English" "eng|English SDH" "|Signs")

episode "$SERIES" "Track Test S01E01.mkv" "E01" "${FULL_AUDIO[@]}" -- "${FULL_SUBS[@]}"
# Same tracks in another order, so a title match has to beat the position.
episode "$SERIES" "Track Test S01E02.mkv" "E02" \
    "dut|Nederlands|440" "eng|English Commentary|880" "eng|English|660" -- \
    "|Signs" "eng|English SDH" "eng|English" "dut|Nederlands"
# No English and no Signs, so a stored choice finds no match and the global defaults apply.
episode "$SERIES" "Track Test S01E03.mkv" "E03" "dut|Nederlands|440" -- "dut|Nederlands"
episode "$SERIES" "Track Test S01E04.mkv" "E04" "${FULL_AUDIO[@]}" -- "${FULL_SUBS[@]}"

# A second series, to check that a choice stays within its own series.
OTHER="Other Show (2026)/Season 01"
for n in 1 2; do
    episode "$OTHER" "Other Show S01E0$n.mkv" "Other E0$n" \
        "dut|Nederlands|440" "eng|English|660" -- "dut|Nederlands" "eng|English"
done

echo "Wrote the test series to $OUT"
