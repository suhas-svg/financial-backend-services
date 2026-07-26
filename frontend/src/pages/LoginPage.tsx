import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { ArrowRight, BadgeCheck, Building2, Eye, EyeOff, Landmark, LockKeyhole, ShieldCheck, Sparkles, UserRound } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, Navigate, useNavigate, useSearchParams } from "../routing";
import { Button, ErrorNotice, Input } from "../components/ui";
import { login } from "../lib/queries";
import { loginSchema, type LoginValues } from "../lib/schemas";
import { decodeJwtPayload } from "../lib/session";
import { useAuth } from "../state/useAuth";

type Portal = "customer" | "admin";

const portalContent = {
  customer: {
    label: "Customer banking",
    eyebrow: "Personal banking",
    title: "Welcome back",
    detail: "Sign in to manage your accounts, payments, statements, and security.",
    icon: UserRound
  },
  admin: {
    label: "Admin operations",
    eyebrow: "Secure operations",
    title: "Operations sign in",
    detail: "Access monitoring, investigations, account controls, and risk workflows.",
    icon: Building2
  }
} satisfies Record<Portal, { label: string; eyebrow: string; title: string; detail: string; icon: typeof UserRound }>;

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { session, loginWithToken } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [portalError, setPortalError] = useState<string>();
  const portal: Portal = searchParams.get("portal") === "admin" ? "admin" : "customer";
  const content = portalContent[portal];
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { username: "", password: "" } });
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      const token = data.token ?? data.accessToken;
      if (!token) {
        setPortalError("Login response did not include a token");
        return;
      }

      const roles = decodeJwtPayload(token).roles ?? [];
      if (portal === "admin" && !roles.includes("ROLE_ADMIN")) {
        setPortalError("This account does not have access to the Operations Console.");
        return;
      }

      loginWithToken(token);
      navigate(portal === "admin" ? "/admin" : "/");
    }
  });

  const selectPortal = (nextPortal: Portal) => {
    setPortalError(undefined);
    mutation.reset();
    setSearchParams(nextPortal === "admin" ? { portal: "admin" } : {}, { replace: true });
  };

  if (session) {
    return <Navigate to={portal === "admin" && session.roles.includes("ROLE_ADMIN") ? "/admin" : "/"} replace />;
  }

  return (
    <main className="auth-shell relative min-h-screen bg-[#050b14] lg:grid lg:grid-cols-[minmax(0,1.08fr)_minmax(28rem,.92fr)]">
      <header className="absolute inset-x-0 top-0 z-30 flex justify-center px-4 py-6 sm:py-8">
        <div className="flex items-center gap-3 text-white">
          <span className="grid h-12 w-12 place-items-center rounded-2xl border border-emerald-300/20 bg-emerald-300/10 shadow-lg shadow-black/20 backdrop-blur">
            <Landmark className="h-6 w-6 text-emerald-300" />
          </span>
          <div>
            <p className="text-xl font-semibold tracking-[-0.025em] sm:text-2xl">Financial Console</p>
            <p className="text-center text-xs text-emerald-100/55">Simple. Secure. In control.</p>
          </div>
        </div>
      </header>

      <section className="auth-story relative hidden min-h-screen overflow-hidden px-10 pb-10 pt-28 text-white lg:flex lg:flex-col lg:justify-center xl:px-16">
        <div className="relative z-10 max-w-2xl py-12">
          <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-emerald-200/15 bg-emerald-100/10 px-3 py-1.5 text-xs font-semibold text-emerald-100 backdrop-blur">
            <Sparkles className="h-3.5 w-3.5" />
            Your finances, clearly connected
          </div>
          <h1 className="max-w-xl text-4xl font-semibold leading-[1.08] tracking-[-0.04em] xl:text-6xl">A smarter way to manage every financial move.</h1>
          <p className="mt-6 max-w-lg text-base leading-7 text-slate-300 xl:text-lg">One secure workspace for everyday banking and the operational teams that keep it moving.</p>

          <div className="mt-10 grid max-w-xl gap-3 sm:grid-cols-2">
            <div className="auth-glass-card rounded-2xl border border-white/10 p-4 backdrop-blur">
              <ShieldCheck className="h-5 w-5 text-emerald-300" />
              <p className="mt-4 text-sm font-semibold">Protected access</p>
              <p className="mt-1 text-xs leading-5 text-slate-400">Role-aware sessions and secure account controls.</p>
            </div>
            <div className="auth-glass-card rounded-2xl border border-white/10 p-4 backdrop-blur">
              <BadgeCheck className="h-5 w-5 text-cyan-300" />
              <p className="mt-4 text-sm font-semibold">Real-time clarity</p>
              <p className="mt-1 text-xs leading-5 text-slate-400">Balances, activity, and operational insight in one place.</p>
            </div>
          </div>
        </div>

        <p className="absolute bottom-10 left-10 z-10 text-xs text-slate-500 xl:left-16">Secure access for customers and authorized operations staff.</p>
      </section>

      <section className="auth-form-panel relative flex min-h-screen items-center justify-center overflow-hidden bg-[#07101d] px-4 pb-8 pt-32 sm:px-8 lg:px-12 lg:pt-28">
        <div className="auth-mobile-glow absolute inset-x-0 top-0 h-72 lg:hidden" aria-hidden="true" />
        <div className="relative z-10 w-full max-w-md">
          <div className="auth-dark-card rounded-[1.75rem] border border-slate-700/70 bg-slate-900/90 p-5 shadow-[0_30px_90px_-35px_rgba(0,0,0,.85)] backdrop-blur-xl sm:p-8">
            <div className="grid grid-cols-2 gap-1 rounded-2xl border border-slate-800 bg-slate-950/80 p-1" aria-label="Choose sign in portal">
              {(Object.keys(portalContent) as Portal[]).map((option) => {
                const OptionIcon = portalContent[option].icon;
                const selected = portal === option;
                return (
                  <button
                    key={option}
                    type="button"
                    aria-pressed={selected}
                    className={`flex min-h-12 items-center justify-center gap-2 rounded-xl px-3 text-xs font-semibold transition sm:text-sm ${selected ? "bg-slate-800 text-white shadow-sm ring-1 ring-slate-700" : "text-slate-500 hover:bg-slate-900 hover:text-slate-200"}`}
                    onClick={() => selectPortal(option)}
                  >
                    <OptionIcon className={`h-4 w-4 ${selected ? (option === "admin" ? "text-cyan-400" : "text-emerald-400") : ""}`} />
                    {portalContent[option].label}
                  </button>
                );
              })}
            </div>

            <div className="mb-7 mt-8">
              <p className={`text-xs font-bold uppercase tracking-[.2em] ${portal === "admin" ? "text-cyan-400" : "text-emerald-500"}`}>{content.eyebrow}</p>
              <h2 className="mt-2 text-3xl font-semibold tracking-[-0.035em] text-white">{content.title}</h2>
              <p className="mt-2 text-sm leading-6 text-slate-400">{content.detail}</p>
            </div>

            <form className="grid gap-5" onSubmit={form.handleSubmit((values) => { setPortalError(undefined); mutation.mutate(values); })}>
              <ErrorNotice message={portalError ?? (mutation.error instanceof Error ? mutation.error.message : undefined)} />

              <div className="grid gap-2 text-sm">
                <label className="font-semibold text-slate-200" htmlFor="login-username">Username</label>
                <span className="relative block">
                  <UserRound className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input id="login-username" className="auth-dark-input h-12 rounded-xl border-slate-700 bg-slate-950/70 pl-10 pr-4 text-white placeholder:text-slate-600" autoComplete="username" placeholder={portal === "admin" ? "Enter your admin username" : "Enter your username"} {...form.register("username")} />
                </span>
                {form.formState.errors.username?.message ? <span className="text-xs text-danger">{form.formState.errors.username.message}</span> : null}
              </div>

              <div className="grid gap-2 text-sm">
                <label className="font-semibold text-slate-200" htmlFor="login-password">Password</label>
                <span className="relative block">
                  <LockKeyhole className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input id="login-password" className="auth-dark-input h-12 rounded-xl border-slate-700 bg-slate-950/70 pl-10 pr-12 text-white placeholder:text-slate-600" type={showPassword ? "text" : "password"} autoComplete="current-password" placeholder="Enter your password" {...form.register("password")} />
                  <button type="button" className="absolute right-2 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-lg text-slate-500 transition hover:bg-slate-800 hover:text-slate-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-500" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? "Hide password" : "Show password"}>
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </span>
                {form.formState.errors.password?.message ? <span className="text-xs text-danger">{form.formState.errors.password.message}</span> : null}
              </div>

              <Button type="submit" disabled={mutation.isPending} className={`mt-1 h-12 w-full rounded-xl shadow-lg ${portal === "admin" ? "bg-cyan-700 hover:bg-cyan-600" : "bg-emerald-700 hover:bg-emerald-600"}`}>
                {mutation.isPending ? "Signing in..." : "Sign in"}
                {!mutation.isPending ? <ArrowRight className="h-4 w-4" /> : null}
              </Button>
            </form>

            <div className="mt-7 border-t border-slate-800 pt-6 text-center text-sm text-slate-400">
              {portal === "customer" ? (
                <p>New to Financial Console? <Link className="font-semibold text-emerald-400 transition hover:text-emerald-300" to="/register">Create an account</Link></p>
              ) : (
                <p>Operations access is limited to authorized team members.</p>
              )}
            </div>
          </div>

          <p className="mt-6 flex items-center justify-center gap-2 text-center text-xs text-slate-500"><ShieldCheck className="h-3.5 w-3.5 text-emerald-500" />Your session is protected and securely managed.</p>
        </div>
      </section>
    </main>
  );
}
