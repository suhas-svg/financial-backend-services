/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#17202a",
        muted: "#5d6978",
        panel: "#ffffff",
        line: "#d8dee8",
        brand: "#0f766e",
        accent: "#2563eb",
        warning: "#b45309",
        danger: "#b91c1c"
      },
      boxShadow: {
        subtle: "0 1px 2px rgba(15, 23, 42, 0.08)"
      }
    }
  },
  plugins: []
};
