<script lang="ts">
	type ImageItem = {
		file: string;
		alt: string;
	};

	let props = $props<{
		images: ImageItem[];
		gap?: string;
		height?: string;
	}>();
</script>

<div class="image-bar">
	{#each props.images as { file, alt }}
		<div class="image-container">
			<img
				src={file}
				{alt}
				loading="lazy"
			/>
		</div>
	{/each}
</div>

<style>
    .image-bar {
        display: flex;
        flex-wrap: nowrap;
        gap: var(--gap, 1rem);
        overflow-x: auto;
        padding: 1rem;
        scrollbar-width: thin;
        -webkit-overflow-scrolling: touch;
    }

    @media (max-width: 600px) {
        .image-bar {
            flex-direction: column;
        }
    }

    .image-container {
        flex: 0 0 auto;
        height: var(--height, 200px);
    }

    img {
        height: 100%;
        width: auto;
        object-fit: cover;
        border-radius: 0.5rem;
    }

    /* Hide scrollbar for Chrome, Safari and Opera */
    .image-bar::-webkit-scrollbar {
        display: none;
    }

    /* Hide scrollbar for IE, Edge and Firefox */
    .image-bar {
        -ms-overflow-style: none;
        scrollbar-width: none;
    }
</style>
