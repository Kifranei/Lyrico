# Metadata Processing

Controls text processing and display-related settings for metadata.

## Chinese Text Conversion

"Chinese Text Conversion" applies to searched text metadata and lyrics. IDs, dates, numbers, URLs, and covers are not affected.

Options:
- None
- Simplified → Traditional
- Traditional → Simplified

## Remove Empty Lines

When enabled, empty lines are automatically removed from lyrics and other multi-line text fields on save.

## Non-Lyric Content Filtering

Used when "Remove non-lyric content" is checked during lyric formatting. Automatically strips composer, lyricist, source, and other non-lyric lines.

Filtering rules are managed at `Settings` → `Metadata Processing` → `Non-Lyric Content Filtering Rules`. Rules match entire lines of the original lyric text; word-timed lyrics are joined into lines before matching.

Common filter examples: `Composer:`, `Lyricist:`, `Arranger:`, `Source: QQ Music`, etc.

## Artist Separator

The separator used when writing multiple artists to a tag. The default separator varies by audio format (e.g., FLAC uses `\0`, MP3 uses `; `).

## Artist Splitting

**Artist Split Rules** affect how the Artists view groups songs. When enabled, Lyrico lists songs with multiple artists under each artist separately.

In the split settings you can configure:
- Additional separators (e.g., `feat.`, `&`, `,`).
- **No-Split Whitelist**: Artist names that should never be split (e.g., names that naturally contain separator characters).

After modifying separators or the whitelist, you usually need to tap **Rebuild Artist Index** for changes to fully take effect.

## Field Visibility Settings

Controls which tag fields appear in the single-song editor and batch editor.

Hidden fields:
- Do not appear in the editing interface.
- Are **not cleared** on save (existing tag data is preserved).

Use this to simplify the editor by hiding fields you rarely use.

## Custom Tags

Controls which custom tag keys are shown in the editor and batch editor. Go to: `Settings` → `Metadata Processing` → `Custom Tag Management`.

- The tags in the list are exactly the tag fields that appear in the single-song and batch editors, in the same order.
- Tap **Add Tag** to add a tag:
  - Pick a scanned key directly from **Tags found in the library**, ordered by usage count (most used first).
  - Or type a key by hand (for example `SOURCE`, `MOOD`). Keys are trimmed and upper-cased automatically; empty input, line breaks, more than 64 characters, or a duplicate reports the specific reason.
- Tap a tag in the list to see **which songs use it**; tapping a song there opens its editor.
- Deleting a tag only removes it from the list—it does **not** delete existing data from songs, and re-adding it brings the values back into view.
- **Reset to Default** clears the list back to its default state.

Tag keys come from the audio files themselves, and Lyrico tracks how often each key is used.

## Character Mapping

Used during batch rename to replace specific characters in filenames.

In the character mapping page, tap a character to configure its replacement rule. Each character can map to a different replacement value. Common usage: replacing `/` with `-`, or `:` with `_` for characters unsuitable in filenames.
