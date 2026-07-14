import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, CheckCircle2, Eye, EyeOff, Landmark, LockKeyhole, ShieldCheck, Sparkles, UserRound } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, Navigate } from "react-router-dom";
import { Button, ErrorNotice, Input } from "../components/ui";
import { register } from "../lib/queries";
import { registerSchema, type RegisterValues } from "../lib/schemas";
import { useAuth } from "../state/useAuth";

export function RegisterPage() {
  const { session } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const form = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), defaultValues: { username: "", password: "" } });
  const mutation = useMutation({ mutationFn: register });

  if (session) {
    return <Navigate to="/" replace />;
  }

  return (
    <main className="auth-shell relative min-h-screen bg-[#050b14] lg:grid lg:grid-cols-[minmax(0,1.08fr)_minmax(28rem,.92fr)]">
      <header className="absolute inset-x-0 top-0 z-30 flex justify-center px-4 py-6 sm:py-8">
        <Link to="/login" className="flex items-center gap-3 text-white" aria-label="Financial Console login">
          <span className="grid h-12 w-12 place-items-center rounded-2xl border border-emerald-300/20 bg-emerald-300/10 shadow-lg shadow-black/20 backdrop-blur">
            <Landmark className="h-6 w-6 text-emerald-300" />
          </span>
          <div>
            <p className="text-xl font-semibold tracking-[-0.025em] sm:text-2xl">Financial Console</p>
            <p className="text-center text-xs text-emerald-100/55">Simple. Secure. In control.</p>
          </div>
        </Link>
      </header>

      <section className="auth-story relative hidden min-h-screen overflow-hidden px-10 pb-10 pt-28 text-white lg:flex lg:flex-col lg:justify-center xl:px-16">
        <div className="relative z-10 max-w-2xl py-12">
          <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-emerald-200/15 bg-emerald-100/10 px-3 py-1.5 text-xs font-semibold text-emerald-100 backdrop-blur">
            <Sparkles className="h-3.5 w-3.5" />
            Banking built around you
          </div>
          <h1 className="max-w-xl text-4xl font-semibold leading-[1.08] tracking-[-0.04em] xl:text-6xl">Your financial home starts here.</h1>
          <p className="mt-6 max-w-lg text-base leading-7 text-slate-300 xl:text-lg">Create secure customer access and bring accounts, transfers, statements, and protection into one clear workspace.</p>

          <div className="mt-10 grid max-w-lg gap-4 text-sm text-slate-300">
            {["See balances and activity at a glance", "Move money with built-in safeguards", "Manage statements, recipients, and security"].map((benefit) => (
              <div key={benefit} className="flex items-center gap-3">
                <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-emerald-300/10 text-emerald-300"><CheckCircle2 className="h-4 w-4" /></span>
                {benefit}
              </div>
            ))}
          </div>
        </div>
        <p className="absolute bottom-10 left-10 z-10 text-xs text-slate-500 xl:left-16">Customer registrations receive secure personal banking access.</p>
      </section>

      <section className="auth-form-panel relative flex min-h-screen items-center justify-center overflow-hidden bg-[#07101d] px-4 pb-8 pt-32 sm:px-8 lg:px-12 lg:pt-28">
        <div className="auth-mobile-glow absolute inset-x-0 top-0 h-72 lg:hidden" aria-hidden="true" />
        <div className="relative z-10 w-full max-w-md">
          <div className="auth-dark-card rounded-[1.75rem] border border-slate-700/70 bg-slate-900/90 p-5 shadow-[0_30px_90px_-35px_rgba(0,0,0,.85)] backdrop-blur-xl sm:p-8">
            <Link className="inline-flex items-center gap-2 text-xs font-semibold text-slate-400 transition hover:text-emerald-300" to="/login">
              <ArrowLeft className="h-3.5 w-3.5" />
              Back to sign in
            </Link>

            <div className="mb-7 mt-6">
              <p className="text-xs font-bold uppercase tracking-[.2em] text-emerald-500">Personal banking</p>
              <h2 className="mt-2 text-3xl font-semibold tracking-[-0.035em] text-white">Create your account</h2>
              <p className="mt-2 text-sm leading-6 text-slate-400">Choose your credentials to set up secure customer access.</p>
            </div>

            <form className="grid gap-5" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
              <ErrorNotice message={mutation.error instanceof Error ? mutation.error.message : undefined} />
              {mutation.isSuccess ? (
                <div className="flex gap-3 rounded-xl border border-emerald-500/25 bg-emerald-500/10 p-3 text-sm text-emerald-300">
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>Registered {mutation.data.username}. You can sign in now.</span>
                </div>
              ) : null}

              <div className="grid gap-2 text-sm">
                <label className="font-semibold text-slate-200" htmlFor="register-username">Username</label>
                <span className="relative block">
                  <UserRound className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  <Input id="register-username" className="auth-dark-input h-12 rounded-xl border-slate-700 bg-slate-950/70 pl-10 pr-4 text-white placeholder:text-slate-600" autoComplete="username" placeholder="Choose a username" {...form.register("username")} />
                </span>
                {form.formState.errors.username?.message ? <span className="text-xs text-danger">{form.formState.errors.username.message}</span> : null}
              </div>

              <div className="grid gap-2 text-sm">
                <label className="font-semibold text-slate-200" htmlFor="register-password">Password</label>
                <span className="relative block">
                  <LockKeyhole className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  <Input id="register-password" className="auth-dark-input h-12 rounded-xl border-slate-700 bg-slate-950/70 pl-10 pr-12 text-white placeholder:text-slate-600" type={showPassword ? "text" : "password"} autoComplete="new-password" placeholder="Create a secure password" {...form.register("password")} />
                  <button type="button" className="absolute right-2 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-lg text-slate-500 transition hover:bg-slate-800 hover:text-slate-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-500" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? "Hide password" : "Show password"}>
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </span>
                {form.formState.errors.password?.message ? <span className="text-xs text-danger">{form.formState.errors.password.message}</span> : <span className="text-xs text-slate-500">Use at least 8 characters.</span>}
              </div>

              <Button type="submit" disabled={mutation.isPending} className="mt-1 h-12 w-full rounded-xl bg-emerald-700 shadow-lg hover:bg-emerald-600">
                {mutation.isPending ? "Creating account..." : "Create account"}
                {!mutation.isPending ? <ArrowRight className="h-4 w-4" /> : null}
              </Button>
            </form>

            <p className="mt-7 border-t border-slate-800 pt-6 text-center text-sm text-slate-400">
              Already registered? <Link className="font-semibold text-emerald-400 transition hover:text-emerald-300" to="/login">Sign in</Link>
            </p>
          </div>

          <p className="mt-6 flex items-center justify-center gap-2 text-center text-xs text-slate-500"><ShieldCheck className="h-3.5 w-3.5 text-emerald-500" />Your credentials are handled through secure access controls.</p>
        </div>
      </section>
    </main>
  );
}
