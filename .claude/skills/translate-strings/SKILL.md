---
name: translate-strings
description: >-
  Translate CE-only strings into all 36 supported languages and verify translation completeness.
  Use this skill whenever a PR adds or modifies user-facing strings or plurals in Ephyra (the midasvo/ephyra repo).
---

# Translate Strings Skill

Ephyra (formerly Findroid CE) adds strings and plurals that do not exist in upstream Findroid.
Upstream's translations come from Weblate into `values-<lang>/strings.xml`.
To avoid merge conflicts during upstream syncs, all CE-only translations live in:
`<module>/src/main/res/values-<lang>/strings_ce.xml`.

## 36 Supported Languages

The 36 target languages are:
`ar`, `az`, `b+es+419`, `bg`, `ca`, `cs-rCZ`, `da`, `de`, `es`, `es-rMX`, `et`, `fi`, `fr`, `hi`, `hu`, `in`, `it`, `iw`, `ja`, `ko`, `nl`, `nn`, `pl`, `pt`, `pt-rBR`, `ro`, `ru`, `sk`, `sl`, `sv`, `tr`, `uk`, `vi`, `yue`, `zh-rCN`, `zh-rTW`.

## Rules for CE Translations

1. **Placeholders:** Every placeholder (`%s`, `%d`, `%1$s`, `%2$d`, etc.) in the English string must be present in the translated string.
2. **Escaping:** Single quotes/apostrophes (`'`) must be escaped as `\'`. Double quotes must be escaped as `\"`. Ampersands must be written as `&amp;`.
3. **Plurals:** Plural quantities must follow CLDR rules for that language (e.g., `one`/`few`/`many`/`other` for Slavic languages; `other` for Japanese/Chinese; `zero`/`one`/`two`/`few`/`many`/`other` for Arabic).
4. **String arrays:** Option label arrays in `settings/src/main/res/values/string_arrays.xml` reference `@string/<key>`. Translating the referenced `<string>` translates the array automatically. Value arrays have `translatable="false"` and are never translated.
5. **English changes:** When an English CE string changes in `res/values/strings.xml`, update the translated strings in all 36 `strings_ce.xml` files.
6. **Changing upstream text:** Never edit upstream's `values-<lang>/strings.xml`, not even to put the app name in. Add a CE key (`ce_<upstream key>`, e.g. `ce_welcome`) with its translations in `strings_ce.xml`, and point the code at it. Upstream's key stays as Weblate ships it, so syncs don't conflict.
7. **Upstream collisions:** If an upstream sync introduces a string with the same key, Android AAPT fails on duplicate resources. Simply delete the key from `strings_ce.xml` so upstream's Weblate translation takes over.

## Verification

To verify that all CE-only strings are present and have valid placeholders/quantities:

```bash
python3 .claude/skills/translate-strings/scripts/check-translations.py
```

This script exits with code 0 if all 36 languages are complete and valid, or non-zero with error details.
CI runs this check on every PR to prevent untranslated strings from entering `main`.
