import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [
    react(),
    {
      name: "synthetic-sandbox-classification",
      transformIndexHtml() {
        return {
          tags: [
            { tag: "link", attrs: { rel: "stylesheet", href: "/sandbox-banner.css" }, injectTo: "head" },
            { tag: "script", attrs: { type: "module", src: "/sandbox-runtime.js" }, injectTo: "body" }
          ]
        };
      }
    }
  ],
  build: {
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          // clsx is shared by the app and recharts; left unassigned it would be pulled into
          // vendor-charts and make every page preload the charts library.
          if (id.includes("/node_modules/clsx/")) return "vendor-utils";
          if (id.includes("recharts") || id.includes("d3-") || id.includes("victory-vendor")) return "vendor-charts";
          if (id.includes("@tanstack")) return "vendor-query";
          if (id.includes("react-hook-form") || id.includes("zod")) return "vendor-forms";
          if (id.includes("react")) return "vendor-react";
          return undefined;
        }
      }
    }
  }
});
