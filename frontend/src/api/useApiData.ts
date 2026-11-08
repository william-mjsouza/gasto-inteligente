import { useCallback, useEffect, useState } from 'react';

import { apiRequest } from './client';

interface State<T> {
  data: T | null;
  error: unknown;
}

/** Carrega um recurso da API; {@code reload} busca novamente (por exemplo, após um conflito). */
export function useApiData<T>(path: string) {
  const [state, setState] = useState<State<T>>({ data: null, error: null });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    apiRequest<T>(path)
      .then((data) => {
        if (active) setState({ data, error: null });
      })
      .catch((error: unknown) => {
        if (active) setState((current) => ({ data: current.data, error }));
      });
    return () => {
      active = false;
    };
  }, [path, reloadKey]);

  const reload = useCallback(() => {
    setReloadKey((key) => key + 1);
  }, []);

  const setData = useCallback((data: T) => {
    setState({ data, error: null });
  }, []);

  return { data: state.data, error: state.error, reload, setData };
}
