import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { configurarSesion } from '../../api/cliente'
import type { Sesion } from './api'
import { SesionContext } from './sesionContext'

const CLAVE_ALMACEN = 'estudio-documental.sesion'

function leerSesionGuardada(): Sesion | null {
  try {
    const texto = localStorage.getItem(CLAVE_ALMACEN)
    if (!texto) return null
    const sesion = JSON.parse(texto) as Sesion
    return new Date(sesion.expiraEn) > new Date() ? sesion : null
  } catch {
    return null
  }
}

/** Guarda la sesión actual y la comparte con toda la aplicación. */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(leerSesionGuardada)

  const iniciar = useCallback((nueva: Sesion) => {
    localStorage.setItem(CLAVE_ALMACEN, JSON.stringify(nueva))
    setSesion(nueva)
  }, [])

  const cerrar = useCallback(() => {
    localStorage.removeItem(CLAVE_ALMACEN)
    setSesion(null)
  }, [])

  // Se conecta durante el render (y no en un efecto) para que las consultas de los componentes
  // hijos ya tengan el token en su primer render. Es una asignación idempotente.
  configurarSesion({
    obtenerToken: () => sesion?.token ?? null,
    alExpirarSesion: cerrar,
  })

  const valor = useMemo(() => ({ sesion, iniciar, cerrar }), [sesion, iniciar, cerrar])
  return <SesionContext value={valor}>{children}</SesionContext>
}
