import { render } from '@testing-library/react'
import { vi } from 'vitest'
import { App } from '../app/App'

/** Abre la aplicación en `ruta` con una sesión de ADMIN ya iniciada. */
export function abrirComoAdmin(ruta: string) {
  localStorage.setItem(
    'estudio-documental.sesion',
    JSON.stringify({
      token: 'token-de-prueba',
      expiraEn: new Date(Date.now() + 3_600_000).toISOString(),
      usuario: { id: '1', usuario: 'admin', nombre: 'Ana Pérez', rol: 'ADMIN' },
    }),
  )
  window.history.pushState({}, '', ruta)
  return render(<App />)
}

export function respuestaJson(status: number, cuerpo: unknown) {
  return new Response(status === 204 ? null : JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Manejador = (url: string, init?: RequestInit) => Response | Promise<Response>

/** Simula el backend: cada clave es "MÉTODO /ruta" (sin /api/v1 ni parámetros). */
export function simularApi(rutas: Record<string, Manejador>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (entrada, init) => {
    const url = String(entrada)
    const ruta = url.replace('/api/v1', '').split('?')[0]
    const clave = `${init?.method ?? 'GET'} ${ruta}`
    const manejador = rutas[clave]
    if (!manejador) {
      return respuestaJson(404, { status: 404, detail: `Ruta no simulada: ${clave}` })
    }
    return manejador(url, init)
  })
}

export const paginaVacia = { contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 }
