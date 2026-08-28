<script lang="ts">
	let props = $props<{
		src: string;
		poster?: string;
		label?: string;
	}>();
</script>

<video
	src={props.src}
	poster={props.poster}
	aria-label={props.label}
	autoplay
	muted
	loop
	playsinline
	preload="metadata"
></video>

<style>
    video {
        display: block;
        width: 100%;
        max-width: min(640px, calc(100vw - 2rem));
        height: auto;
        border-radius: 0.5rem;
        margin: 1rem auto;
    }
</style>
