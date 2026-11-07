# Waivers

Only the owner can waive a gate item (R10).

## 2026-11-07: Gate 0 no longer blocks app work

The owner asked to speed up and start on the app ("سرع العملية وابدأ في التطبيق").

- Phase 1 and the app screens start now, before Gate 0 is complete.
- UI work is allowed before Gate 0 passes, which R10 would otherwise forbid.
- Gate 0 itself is not waived. Its checks keep running beside the app work, and the owner still reviews them.
- If a Gate 0 result forces an engine change, the app work adapts. `:player` only talks to `FramePlayer`, so that change stays inside `:engine`.

## 2026-11-07: the app finds videos by itself

The owner asked that the app search the whole phone and show every video, with films and series in the same cards, rating and progress bar. This replaces "Scan only folders the owner adds" in brief section 4.6. The + button stays for extra places such as a USB drive.

## 2026-11-07: Media Manager and file deletion

The owner asked for a Media Manager section in Settings that shows the size of folders and videos, and asked to add deletion with a confirmation before every delete. This replaces "File rename or delete from inside the app" in brief section 8, for delete only. Rename stays out.

## 2026-11-07: Private folder

The owner asked for a private folder: videos can be moved into it, and only a password or the owner's fingerprint opens it. This replaces "private vault" in brief section 8, and adds the USE_BIOMETRIC permission and androidx.biometric to sections 4.10 and 4.2.

## 2026-11-07: Themes and colours

The owner asked for customisation in Settings: colours and themes, with light, dark or follow the phone. This replaces F-U12 "One look" and the "no theme choice" line in the brief. The default stays dark, and the player stays dark in every theme.

## 2026-11-07: GitHub backup

The owner asked to upload the project to their own GitHub account and leave it there, and said they might publish it one day. This replaces "Never upload, push, publish or sync" and "No GitHub or any git remote" in R1, for one repository under the owner's personal account, set to private. Making it public stays the owner's decision.

Because it may go public later, the history stays clean from the first commit: no signing material, API keys, device serials, phone screenshots, the owner's video names, Nuvio reference images or test media.

The rest of R1 stands: no team-knowledge, no `knowledge_sync.py` or `skills_sync.py`, no lessons inbox, no skill, no hosted page or Artifact.

## 2026-11-07: APK on GitHub

The owner asked that each finished version be uploaded as soon as it is ready, so they can download it from GitHub Releases and update the app themselves. `.\fg.ps1 release` publishes it. Releases are not part of git history. Before the repository ever goes public, decide which ones stay, because an APK carries GPL-3.0 duties.

## 2026-11-08: TMDB and IMDb switches

The owner asked for an on/off switch for TMDB and for IMDb on the keys page, each keeping its saved key, and for OMDb to identify new titles by name and year while TMDB is off, with the poster, plot, genres, cast names, runtime and IMDb rating, and episode titles and ratings for series. This changes the brief as follows:

- F-M10 "Never use OMDb posters" no longer holds for a title matched through OMDb. Its poster comes from OMDb's poster host, `m.media-amazon.com`, which joins the allow-list of section 4.9. The IMDb switch blocks that host with `www.omdbapi.com`.
- F-M10 "Show the TMDB rating by default": with TMDB off, no TMDB rating is shown anywhere, and cards show the IMDb rating where one exists.
- F-M1 "Search TMDB": while TMDB is off or has no key, OMDb matches instead. R2 still holds: automatic OMDb lookups send only the parsed title, year and kind, IMDb ids and season numbers. Fix match sends what the owner types, as it does to TMDB.
- F-M5 and F-M14: a title matched through OMDb has no backdrop, title logo, cast photo, character, collection, episode still or Arabic text. Hero and Details show its poster whole instead.
- F-M11 is TMDB's rule. OMDb data is refreshed on the same clock but never purged by it.

Turning TMDB back on looks up each OMDb title by its IMDb id, and keeps progress, favourites, manual covers and manual matches.
