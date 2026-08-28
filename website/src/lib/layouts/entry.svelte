<script>
	const { children } = $props();
</script>

<main>
	<a href="/">Back to overview</a>
	{@render children()}
</main>
<footer>
	<a href="https://github.com/dxviie/genuary-2026" target="_blank">GitHub</a>
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

    :global(main > :not(video):not(pre)) {
        max-width: 42rem;
    }

    :global(h1) {
        text-align: center;
    }

    @media (max-width: 600px) {
        :global(h1) {
            font-size: 1.5rem;
        }
    }

    :global(pre[class*='language-']) {
        background: #f4f4ef;
        border: 1px dashed #000;
        border-radius: .5rem;
        padding: 1rem;
        overflow-x: auto;
        max-width: min(42rem, calc(100vw - 2rem));
        font-size: .8rem;
        line-height: 1.2rem;
    }

    :global(code) {
        font-family: "Courier New", monospace;
    }

    :global(.token.keyword) { color: #0077aa; }
    :global(.token.string) { color: #669900; }
    :global(.token.comment) { color: #939393; }
    :global(.token.function) { color: #dd4a68; }
    :global(.token.number) { color: #990055; }
    :global(.token.operator) { color: #9a6e3a; }
    :global(.token.class-name) { color: #116644; }
    :global(.token.annotation) { color: #9a6e3a; }

    :global(footer) {
        text-align: center;
        padding: 1rem;
    }

    :global(footer a) {
        margin: 0 .5rem;
    }
</style>
