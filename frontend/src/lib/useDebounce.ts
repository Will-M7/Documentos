import { useEffect, useState } from 'react'

/** Devuelve `valor` solo cuando deja de cambiar durante `ms` milisegundos (para búsquedas al escribir). */
export function useDebounce<T>(valor: T, ms = 300): T {
  const [estable, setEstable] = useState(valor)
  useEffect(() => {
    const temporizador = setTimeout(() => setEstable(valor), ms)
    return () => clearTimeout(temporizador)
  }, [valor, ms])
  return estable
}
