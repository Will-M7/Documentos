import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { configurarSesion } from '../../api/cliente'
import { cerrarSesionEnServidor, type Sesion } from './api'
import { SesionContext } from './sesionContext'

const CLAVE_ALMACEN = 'estudio-documental.sesion'

function leerSesionGuardada(): Sesion | null {
  try {
    const texto = localStorage.getItem(CLAVE_ALMACEN)
    if (!texto) return null
    const sesion = JSON.parse(texto) as Sesion
    // Vale mientras se pueda renovar, aunque el token de acceso ya haya vencido
    return sesion.tokenSesion && new Date(sesion.sesionExpiraEn) > new Date() ? sesion : null
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
    const guardada = leerSesionGuardada()
    if (guardada) cerrarSesionEnServidor(guardada.tokenSesion)
    localStorage.removeItem(CLAVE_ALMACEN)
    setSesion(null)
  }, [])

  // Se conecta durante el render (y no en un efecto) para que las consultas de los componentes
  // hijos ya tengan el token en su primer render. Es una asignación idempotente.
  configurarSesion({
    obtenerToken: () => sesion?.token ?? null,
    obtenerTokenSesion: () => sesion?.tokenSesion ?? null,
    alRenovar: iniciar,
    alExpirarSesion: cerrar,
  })

  const valor = useMemo(() => ({ sesion, iniciar, cerrar }), [sesion, iniciar, cerrar])
  return <SesionContext value={valor}>{children}</SesionContext>
}
