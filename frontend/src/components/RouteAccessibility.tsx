import { useEffect, useState } from "react";
import { SESSION_EXPIRED_EVENT } from "../lib/session";
import { useLocation } from "../routing";

export function RouteAccessibility() {
  const location = useLocation();
  const [announcement, setAnnouncement] = useState("");

  useEffect(() => {
    const target = document.querySelector<HTMLElement>("main");
    if (target) {
      target.tabIndex = -1;
      target.focus({ preventScroll: true });
    }
    setAnnouncement(`Loaded ${document.title || "Financial Console"}`);
  }, [location.pathname]);

  useEffect(() => {
    const expired = () => setAnnouncement("Your session expired. Sign in again to continue.");
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  }, []);

  return (
    <>
      <a className="skip-link" href="#main-content">Skip to main content</a>
      <div className="sr-only" aria-live="polite" aria-atomic="true">{announcement}</div>
    </>
  );
}
