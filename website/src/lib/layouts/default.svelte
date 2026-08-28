<script>
	const { children } = $props();
</script>

<main>
	<h1>Genuary 2026</h1>
	<p class="intro">
		Here you can find <a href="https://www.d17e.dev">d17e</a>'s entries for <a href="https://genuary.art">Genuary 2026</a>.
		This year everything was made in Kotlin with <a href="https://openrndr.org">OPENRNDR</a>, which runs on the desktop
		rather than in the browser &mdash; so each page shows recorded output of the real sketch, straight from a scripted,
		reproducible render (<code>./gradlew renderSiteMedia</code>).
	</p>
	{@render children()}
</main>
<footer>
	<a href="https://github.com/dxviie/genuary-2026" target="_blank">GitHub</a>
	<a href="https://github.com/dxviie/genuary-2025" target="_blank">Genuary 2025</a>
	<a href="https://forms.d17e.dev/contact" target="_blank">Contact</a>
</footer>

<style>
    :global(body) {
        font-family: "Courier New", serif;
        line-height: 1.4rem;
        margin: 0;
    }

    :global(a) {
        color: #000;
        text-decoration: none;
        border-bottom-style: dashed;
        border-bottom-width: 1px;
    }

    :global(a:hover) {
        color: darkorange;
    }

    :global(main) {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 1rem .5rem;
    }

    :global(h1) {
        text-align: center;
    }

    .intro {
        max-width: 42rem;
    }

    @media (max-width: 600px) {
        :global(h1) {
            font-size: 1.5rem;
        }
    }

    :global(footer) {
        text-align: center;
        padding: 1rem;
    }

    :global(footer a) {
        margin: 0 .5rem;
    }
</style>
