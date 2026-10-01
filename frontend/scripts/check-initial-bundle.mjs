/* global URL, console, process -- Node build script */
// Fails the build if a heavy, page-specific vendor chunk is loaded on every page.
// The charts library (~355 kB) must only be fetched by pages that draw a chart.
import { readFileSync } from "node:fs";

const html = readFileSync(new URL("../dist/index.html", import.meta.url), "utf8");
const initial = [...html.matchAll(/<(?:script|link)[^>]+(?:src|href)="([^"]+\.js)"/g)].map((match) => match[1]);
const forbidden = initial.filter((file) => /vendor-charts/.test(file));

if (forbidden.length) {
  console.error(`Initial page load pulls in page-specific chunks: ${forbidden.join(", ")}`);
  process.exit(1);
}
console.log(`Initial load: ${initial.length} script(s), no chart chunks.`);
