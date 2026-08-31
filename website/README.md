# genuary-2026 website

The website for [d17e](https://www.d17e.dev)'s Genuary 2026 entries — the sibling of
[genuary-2025](https://github.com/dxviie/genuary-2025).

The 2026 sketches are Kotlin/[OPENRNDR](https://openrndr.org) programs that run on the
desktop, not in the browser. So instead of running the sketches live like the 2025 site
does, this site shows **recorded output of the real sketches**: every prompt has a
scripted, seeded render variant in [`src/main/kotlin/render/`](../src/main/kotlin/render)
that replays the interaction and records a fixed-length clip.

## Regenerating the media

From the repository root (needs a display; on a headless Linux box prefix with
`xvfb-run -a`, plus `ffmpeg` on the PATH):

```bash
./gradlew renderSiteMedia     # renders raw clips into website/media-raw/ (git-ignored)
./gradlew optimizeSiteMedia   # compresses into website/static/NN/ loops, stills, posters
```

Individual renders: `./gradlew renderSite00 | renderSite02 | renderSite05 | renderSite12 | renderSite13Hero | renderSite13Bauhaus`.

The renders are seeded, so re-running produces the same clips (the 13 particle render
keeps a little per-run randomness in its particle jitter).

## Developing the site

```bash
cd website
npm install
npm run dev      # local dev server
npm run build    # production build (Cloudflare adapter)
```

Pages live in `src/routes`: the homepage is `+page.svx`, each prompt is
`src/routes/prompt/NN/+page.svx` (mdsvex — markdown + Svelte). Layouts and the
`VideoLoop`/`ImageDisplay`/`SourceLink` components are in `src/lib`.

## Deploying (Cloudflare Pages, like the 2025 site)

1. Cloudflare dashboard → Workers & Pages → Create → Pages → connect the
   `dxviie/genuary-2026` repo.
2. Build settings:
   - **Root directory**: `website`
   - **Build command**: `npm run build`
   - **Build output directory**: `.svelte-kit/cloudflare`
3. Add the custom domain (e.g. `genuary2026.d17e.dev`) under the project's
   Custom domains tab.
4. Analytics: create a new website entry in the umami dashboard and replace
   `REPLACE-WITH-GENUARY-2026-WEBSITE-ID` in `src/app.html` with its id.

Once connected, Cloudflare Pages builds a preview deployment for every push to
this branch and a production deployment for pushes to the default branch.
