import { mdsvex } from 'mdsvex';
import adapter from '@sveltejs/adapter-cloudflare';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	preprocess: [
		vitePreprocess(),
		mdsvex({
			layout: {
				entry: './src/lib/layouts/entry.svelte',
				_: './src/lib/layouts/default.svelte'
			}
		})
	],

	kit: {
		adapter: adapter()
	},

	extensions: ['.svelte', '.svx']
};

export default config;
