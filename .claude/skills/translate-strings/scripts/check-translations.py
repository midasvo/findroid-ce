#!/usr/bin/env python3
"""
check-translations.py

Checks that all CE-only string resources (strings and plurals) are translated
into all 36 supported languages in their respective <module>/src/main/res/values-<lang>/strings_ce.xml.

Definition of "CE-only":
Any translatable <string> or <plurals> in the default res/values/ of a module
that does NOT exist in upstream/main for that module.

Also verifies:
1. Format specifiers / placeholders (%s, %1$d, %d, etc.) match English source.
2. Plurals contain the required quantity forms for each language.
"""

import os
import re
import sys
import subprocess
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../.."))

LANGUAGES = [
    'ar', 'az', 'b+es+419', 'bg', 'ca', 'cs-rCZ', 'da', 'de', 'es', 'es-rMX',
    'et', 'fi', 'fr', 'hi', 'hu', 'in', 'it', 'iw', 'ja', 'ko',
    'nl', 'nn', 'pl', 'pt', 'pt-rBR', 'ro', 'ru', 'sk', 'sl', 'sv',
    'tr', 'uk', 'vi', 'yue', 'zh-rCN', 'zh-rTW'
]

MODULES = ["core", "data", "settings", "setup", "player/local"]

PLURAL_REQUIRED_QUANTITIES = {
    # Asian / agglutinative / no plural inflections (only 'other' needed in Android)
    'az': ['other'],
    'in': ['other'],
    'ja': ['other'],
    'ko': ['other'],
    'tr': ['other'],
    'vi': ['other'],
    'yue': ['other'],
    'zh-rCN': ['other'],
    'zh-rTW': ['other'],

    # Standard one/other
    'b+es+419': ['one', 'other'],
    'bg': ['one', 'other'],
    'ca': ['one', 'other'],
    'da': ['one', 'other'],
    'de': ['one', 'other'],
    'es': ['one', 'other'],
    'es-rMX': ['one', 'other'],
    'et': ['one', 'other'],
    'fi': ['one', 'other'],
    'fr': ['one', 'other'],
    'hi': ['one', 'other'],
    'hu': ['one', 'other'],
    'it': ['one', 'other'],
    'nl': ['one', 'other'],
    'nn': ['one', 'other'],
    'pt': ['one', 'other'],
    'pt-rBR': ['one', 'other'],
    'sv': ['one', 'other'],

    # Slavic / Eastern European
    'cs-rCZ': ['one', 'few', 'other'],
    'sk': ['one', 'few', 'other'],
    'pl': ['one', 'few', 'many', 'other'],
    'ru': ['one', 'few', 'many', 'other'],
    'uk': ['one', 'few', 'many', 'other'],
    'sl': ['one', 'two', 'few', 'other'],
    'ro': ['one', 'few', 'other'],

    # Semitic
    'iw': ['one', 'other'], # 'two'/'many' optional, 'one' and 'other' mandatory
    'ar': ['zero', 'one', 'two', 'few', 'many', 'other'],
}

import shutil

def get_git_cmd():
    # If on Windows or WSL calling Windows git when worktree was created by Windows git
    if shutil.which("git.exe"):
        return "git.exe"
    return "git"

def get_git_file(ref, rel_path):
    git = get_git_cmd()
    repo_arg = REPO_ROOT
    if git.endswith(".exe") and repo_arg.startswith("/mnt/c/"):
        repo_arg = "C:/" + repo_arg[7:]
    elif git.endswith(".exe") and repo_arg.startswith("/mnt/"):
        drive = repo_arg[5]
        repo_arg = f"{drive.upper()}:/" + repo_arg[7:]

    try:
        res = subprocess.run([git, "-C", repo_arg, "show", f"{ref}:{rel_path}"],
                             capture_output=True, text=True, check=True)
        return res.stdout
    except subprocess.CalledProcessError as e:
        return None

def parse_xml_elements(xml_text):
    res = {'string': {}, 'plurals': {}}
    if not xml_text:
        return res
    try:
        root = ET.fromstring(xml_text)
    except Exception:
        return res

    for child in root:
        name = child.get('name')
        if not name:
            continue
        if child.tag == 'string':
            if child.get('translatable') == 'false':
                continue
            res['string'][name] = {
                'text': child.text or '',
                'elem': child
            }
        elif child.tag == 'plurals':
            if child.get('translatable') == 'false':
                continue
            items = {}
            for item in child:
                q = item.get('quantity')
                if q:
                    items[q] = item.text or ''
            res['plurals'][name] = {
                'items': items,
                'elem': child
            }
    return res

def extract_placeholders(text):
    if not text:
        return []
    # matches %1$s, %2$d, %s, %d, etc.
    return re.findall(r'%(?:\d+\$)?[a-zA-Z]', text)

def get_ce_only_items():
    ce_items = {'string': {}, 'plurals': {}}

    for mod in MODULES:
        for fname in ["strings.xml"]:
            rel_path = f"{mod}/src/main/res/values/{fname}"
            full_path = os.path.join(REPO_ROOT, rel_path)
            if not os.path.exists(full_path):
                continue
            with open(full_path, "r", encoding="utf-8") as f:
                head_text = f.read()
            upstream_text = get_git_file("upstream/main", rel_path)

            head_parsed = parse_xml_elements(head_text)
            upstream_parsed = parse_xml_elements(upstream_text) if upstream_text else {'string': {}, 'plurals': {}}

            for name, data in head_parsed['string'].items():
                if name not in upstream_parsed['string']:
                    ce_items['string'][(mod, name)] = data['text']

            for name, data in head_parsed['plurals'].items():
                if name not in upstream_parsed['plurals']:
                    ce_items['plurals'][(mod, name)] = data['items']

    return ce_items

def check_translations(report_only=False):
    ce_items = get_ce_only_items()
    total_strings = len(ce_items['string'])
    total_plurals = len(ce_items['plurals'])
    total_keys = total_strings + total_plurals

    print(f"CE-only keys to verify: {total_keys} ({total_strings} strings, {total_plurals} plurals) across {len(LANGUAGES)} languages.")

    errors = []
    missing_by_lang = {}

    for lang in LANGUAGES:
        missing_by_lang[lang] = 0
        lang_strings = {}
        lang_plurals = {}

        for mod in MODULES:
            ce_file = os.path.join(REPO_ROOT, f"{mod}/src/main/res/values-{lang}/strings_ce.xml")
            if os.path.exists(ce_file):
                with open(ce_file, "r", encoding="utf-8") as f:
                    content = f.read()
                parsed = parse_xml_elements(content)
                for k, v in parsed['string'].items():
                    lang_strings[(mod, k)] = v['text']
                for k, v in parsed['plurals'].items():
                    lang_plurals[(mod, k)] = v['items']

        # Check strings
        for (mod, name), en_text in ce_items['string'].items():
            if (mod, name) not in lang_strings:
                errors.append(f"[{lang}] Missing <string name=\"{name}\"> in {mod}")
                missing_by_lang[lang] += 1
            else:
                tr_text = lang_strings[(mod, name)]
                en_ph = sorted(extract_placeholders(en_text))
                tr_ph = sorted(extract_placeholders(tr_text))
                if en_ph != tr_ph:
                    errors.append(f"[{lang}] Placeholder mismatch in {mod}:{name} - EN: {en_ph} vs TR: {tr_ph}")

        # Check plurals
        for (mod, name), en_items in ce_items['plurals'].items():
            if (mod, name) not in lang_plurals:
                errors.append(f"[{lang}] Missing <plurals name=\"{name}\"> in {mod}")
                missing_by_lang[lang] += 1
            else:
                tr_items = lang_plurals[(mod, name)]
                req_quantities = PLURAL_REQUIRED_QUANTITIES.get(lang, ['other'])
                for q in req_quantities:
                    if q not in tr_items:
                        errors.append(f"[{lang}] Missing quantity '{q}' in plurals {mod}:{name}")

    if errors:
        print(f"\nFAILED: Found {len(errors)} translation issues across {len(LANGUAGES)} languages.")
        for e in errors[:50]:
            print(f"  {e}")
        if len(errors) > 50:
            print(f"  ... and {len(errors) - 50} more issues.")
        return 1
    else:
        print(f"\nSUCCESS: All {total_keys} CE-only keys are fully translated in all {len(LANGUAGES)} languages!")
        return 0

if __name__ == "__main__":
    sys.exit(check_translations())
