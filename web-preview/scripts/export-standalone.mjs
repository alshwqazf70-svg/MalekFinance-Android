import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, 'dist');
const mime = { '.woff': 'font/woff', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
const dataUrl = async path => `data:${mime[extname(path)] || 'application/octet-stream'};base64,${(await readFile(path)).toString('base64')}`;
let html = await readFile(resolve(dist, 'index.html'), 'utf8');
const jsPath = html.match(/<script\b[^>]*src="([^"]+)"[^>]*><\/script>/)?.[1];
const cssPath = html.match(/<link\b[^>]*rel="stylesheet"[^>]*href="([^"]+)"[^>]*>/)?.[1];
if (!jsPath || !cssPath) throw new Error('Expected a single Vite entry and stylesheet.');
const asset = path => {
  const full = resolve(dist, path.replace(/^\//, ''));
  if (!full.startsWith(dist + '/')) throw new Error('Asset outside dist');
  return full;
};
let css = await readFile(asset(cssPath), 'utf8');
const urls = [...new Set([...css.matchAll(/url\(([^)]+)\)/g)].map(m => m[1].replace(/^["']|["']$/g, '')))];
for (const url of urls) {
  if (url.startsWith('data:')) continue;
  if (!url.startsWith('/assets/')) throw new Error(`Unexpected external asset: ${url}`);
  css = css.split(url).join(await dataUrl(asset(url)));
}
const js = await readFile(asset(jsPath), 'utf8');
html = html.replace(/<script\b[^>]*src="[^"]+"[^>]*><\/script>/, () => `<script type="module">${js.replace(/<\/script/gi, '<\\/script')}</script>`);
html = html.replace(/<link\b[^>]*rel="stylesheet"[^>]*>/, () => `<style>${css.replace(/<\/style/gi, '<\\/style')}</style>`);
html = html.replace('href="/favicon.svg"', `href="${await dataUrl(resolve(dist, 'favicon.svg'))}"`);
await mkdir(resolve(root, 'exports'), { recursive: true });
const output = resolve(root, 'exports', 'MK-Finance-preview.html');
await writeFile(output, html);
console.log(`Standalone preview: ${output} (${Buffer.byteLength(html)} bytes)`);
