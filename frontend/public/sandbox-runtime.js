/* global document, fetch */
const banner = document.createElement("div");
banner.id = "synthetic-sandbox-banner";
banner.setAttribute("role", "status");
banner.setAttribute("aria-live", "polite");
banner.dataset.verified = "false";
banner.textContent = "SYNTHETIC SANDBOX • NO REAL MONEY • VERIFYING ENVIRONMENT";
document.body.prepend(banner);

fetch("/account-api/api/sandbox/metadata", { credentials: "omit", cache: "no-store" })
  .then((response) => response.ok ? response.json() : Promise.reject(new Error("metadata unavailable")))
  .then((metadata) => {
    if (metadata.synthetic !== true || metadata.realMoney !== false) throw new Error("classification mismatch");
    banner.dataset.verified = "true";
    banner.textContent = "SYNTHETIC SANDBOX • NO REAL MONEY • CONTROLLED BETA";
    document.documentElement.dataset.environment = "synthetic-sandbox";
  })
  .catch(() => {
    banner.textContent = "ENVIRONMENT UNVERIFIED • DO NOT USE REAL DATA";
  });
