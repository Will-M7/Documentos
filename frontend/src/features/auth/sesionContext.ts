import { createContext, useContext } from 'react'
import type { Sesion } from './api'

export interface ValorSesion {
  sesion: Sesion | null
  iniciar: (sesion: Sesion) => void
  cerrar: () => void
}

export const SesionContext = createContext<ValorSesion | null>(null)

export function useSesion(): ValorSesion {
  const valor = useContext(SesionContext)
  if (!valor) {
    throw new Error('useSesion debe usarse dentro de <SesionProvider>')
  }
  return valor
}
