import { Component, type ErrorInfo, type ReactNode } from "react";
import { Link, useLocation } from "../routing";
import { Button } from "./ui";

type Props = { children: ReactNode; resetKey: string };
type State = { error: Error | null };

// A failed lazy import usually means a new release replaced the old chunk files;
// retrying the render cannot help, only reloading the page can.
function isChunkLoadError(error: Error) {
  return /dynamically imported module|Importing a module script failed|Failed to fetch|ChunkLoadError/i.test(error.message);
}

/**
 * Contains a crash to the page that caused it, so the layout, navigation and every
 * other page keep working. Navigating to another route clears the error.
 */
export class RouteErrorBoundaryInner extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Route render failed", error, info.componentStack);
  }

  componentDidUpdate(previous: Props) {
    if (previous.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    const staleRelease = isChunkLoadError(error);
    return (
      <section role="alert" className="grid gap-3 rounded-md border border-red-200 bg-red-50 p-6 text-sm text-danger dark:border-red-900 dark:bg-red-950">
        <h2 className="text-base font-semibold">This page could not be displayed</h2>
        <p>
          {staleRelease
            ? "A new version of the app was released. Reload to continue."
            : "Something went wrong while showing this page. Your accounts and money are not affected."}
        </p>
        <div className="flex flex-wrap items-center gap-3">
          {staleRelease
            ? <Button onClick={() => window.location.reload()}>Reload</Button>
            : <Button onClick={() => this.setState({ error: null })}>Try again</Button>}
          <Link to="/" className="font-medium underline">Go to dashboard</Link>
        </div>
      </section>
    );
  }
}

export function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return <RouteErrorBoundaryInner resetKey={pathname}>{children}</RouteErrorBoundaryInner>;
}
