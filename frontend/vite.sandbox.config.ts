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
          if (id.includes("recharts") || id.includes("d3-")) return "vendor-charts";
          if (id.includes("@tanstack")) return "vendor-query";
          if (id.includes("react-hook-form") || id.includes("zod")) return "vendor-forms";
          if (id.includes("react")) return "vendor-react";
          return undefined;
        }
      }
    }
  }
});
