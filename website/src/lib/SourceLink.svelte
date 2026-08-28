<script lang="ts">
	let props = $props<{
		file: string;
		label?: string;
	}>();

	const base = 'https://github.com/dxviie/genuary-2026/blob/main/src/main/kotlin/';
</script>

<p class="source-link">
	&gt; <a href={base + props.file} target="_blank">{props.label ?? `full source: ${props.file}`}</a>
</p>

<style>
    .source-link {
        font-size: 0.9rem;
    }
</style>
