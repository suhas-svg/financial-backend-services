/* eslint-disable react-refresh/only-export-components -- routing adapters intentionally colocate components and hooks */
import { type AnchorHTMLAttributes, type ReactNode, useState } from "react";
import { Link as WouterLink, Redirect, Router, useLocation as useWouterLocation, useSearchParams as useWouterSearchParams } from "wouter";
import { memoryLocation } from "wouter/memory-location";

type LinkProps = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href" | "className"> & {
  to: string;
  className?: string | ((state: { isActive: boolean }) => string);
  end?: boolean;
};

export function Link({ to, className, children, ...props }: LinkProps) {
  return <WouterLink href={to} className={typeof className === "string" ? className : undefined} {...props}>{children}</WouterLink>;
}

export function NavLink({ to, end = false, className, children, ...props }: LinkProps) {
  const [location] = useWouterLocation();
  const pathname = location.split("?", 1)[0];
  const isActive = end ? pathname === to : pathname === to || pathname.startsWith(`${to}/`);
  const resolvedClassName = typeof className === "function" ? className({ isActive }) : className;
  return <WouterLink href={to} className={resolvedClassName} {...props}>{children}</WouterLink>;
}

export function Navigate({ to, replace = false }: { to: string; replace?: boolean }) {
  return <Redirect to={to} replace={replace} />;
}

export function useNavigate() {
  const [, navigate] = useWouterLocation();
  return (to: string, options?: { replace?: boolean }) => navigate(to, options);
}

export function useLocation() {
  const [location] = useWouterLocation();
  return { pathname: location.split("?", 1)[0], search: location.includes("?") ? `?${location.split("?")[1]}` : "" };
}

export function useSearchParams() {
  return useWouterSearchParams();
}

export function BrowserRouter({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

export function MemoryRouter({ children, initialEntries = ["/"] }: { children: ReactNode; initialEntries?: string[] }) {
  const [memory] = useState(() => memoryLocation({ path: initialEntries[0] }));
  return <Router hook={memory.hook}>{children}</Router>;
}
