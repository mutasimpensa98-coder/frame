# Owner requests waiting for the next build

The requests of the last round are built and listed in `STATUS.md` and `NEXT.md`. None of them has been seen on the phone yet.

- TMDB and IMDb switches, in the keys page: an on/off switch for each service that keeps the saved key. Off means no calls to that service and no badge from it. With one off, cards and Details show the other rating. With TMDB off and IMDb on, OMDb matches new titles by name and year, and brings the poster, plot, genres, cast names, runtime and IMDb rating. For series it brings episode titles and ratings. It has no backdrops, title logos, cast photos, episode stills or Arabic text, so the screens fall back to the poster. Respect the free OMDb limit of 1,000 calls a day through the existing queue. Clips and private titles never go out.

Found on the phone on 2026-11-07:

- Files pushed by adb have no MediaStore row, so delete calls them permanent. Check the trash path on a real camera or download clip.
