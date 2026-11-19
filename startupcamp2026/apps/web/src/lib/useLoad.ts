import { useCallback, useEffect, useState } from "react";

/**
 * Loads data when the component appears and whenever `deps` change.
 *
 *   const plans = useLoad(() => unwrap(api.GET("/plans")), []);
 *   plans.data, plans.error, plans.reload()
 */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string>();
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let current = true;
    setError(undefined);
    load()
      .then((value) => current && setData(value))
      .catch((e: Error) => current && setError(e.message));
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { data, error, reload };
}
