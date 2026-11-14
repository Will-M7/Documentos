/**
 * Cliente HTTP único para hablar con el backend.
 *
 * - Agrega el token de sesión a cada petición.
 * - Convierte las respuestas de error (Problem Details, RFC 9457) en `ErrorApi`.
 */

/** Error estándar que devuelve el backend. */
export interface Problema {
  title?: string
  status: number
  detail?: string
  errores?: { campo: string; mensaje: string }[]
}

export class ErrorApi extends Error {
  readonly problema: Problema

  constructor(problema: Problema) {
    super(problema.detail ?? problema.title ?? `Error ${problema.status}`)
    this.name = 'ErrorApi'
    this.problema = problema
  }

  get status() {
    return this.problema.status
  }
}

let obtenerToken: () => string | null = () => null
let alExpirarSesion: () => void = () => {}

/** Lo llama el proveedor de sesión para conectar el token con el cliente HTTP. */
export function configurarSesion(opciones: {
  obtenerToken: () => string | null
  alExpirarSesion: () => void
}) {
  obtenerToken = opciones.obtenerToken
  alExpirarSesion = opciones.alExpirarSesion
}

export async function api<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const cabeceras = new Headers(opciones.headers)
  if (opciones.body && !(opciones.body instanceof FormData)) {
    cabeceras.set('Content-Type', 'application/json')
  }
  const token = obtenerToken()
  if (token) {
    cabeceras.set('Authorization', `Bearer ${token}`)
  }

  const respuesta = await fetch(`/api/v1${ruta}`, { ...opciones, headers: cabeceras })

  if (!respuesta.ok) {
    if (respuesta.status === 401 && token) {
      alExpirarSesion()
    }
    throw new ErrorApi(await leerProblema(respuesta))
  }
  if (respuesta.status === 204) {
    return undefined as T
  }
  return (await respuesta.json()) as T
}

async function leerProblema(respuesta: Response): Promise<Problema> {
  try {
    const cuerpo = (await respuesta.json()) as Partial<Problema>
    return { ...cuerpo, status: respuesta.status }
  } catch {
    return {
      status: respuesta.status,
      detail: 'No se pudo conectar con el servidor. Intente nuevamente.',
    }
  }
}
