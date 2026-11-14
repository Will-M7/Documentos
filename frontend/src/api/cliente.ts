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

/** Descarga un archivo protegido (DOCX, PDF) y lo guarda con su nombre. */
export async function descargar(ruta: string, nombrePorDefecto: string) {
  const blob = await obtenerArchivo(ruta)
  const enlace = document.createElement('a')
  enlace.href = URL.createObjectURL(blob.contenido)
  enlace.download = blob.nombre ?? nombrePorDefecto
  enlace.click()
  setTimeout(() => URL.revokeObjectURL(enlace.href), 10_000)
}

/** Obtiene un archivo protegido como Blob (por ejemplo, para mostrar un PDF en pantalla). */
export async function obtenerArchivo(ruta: string): Promise<{ contenido: Blob; nombre: string | null }> {
  const token = obtenerToken()
  const respuesta = await fetch(`/api/v1${ruta}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!respuesta.ok) {
    throw new ErrorApi(await leerProblema(respuesta))
  }
  return { contenido: await respuesta.blob(), nombre: nombreDeCabecera(respuesta.headers) }
}

function nombreDeCabecera(cabeceras: Headers): string | null {
  const valor = cabeceras.get('Content-Disposition') ?? ''
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(valor)
  if (utf8) return decodeURIComponent(utf8[1])
  const simple = /filename="?([^";]+)"?/i.exec(valor)
  return simple ? simple[1] : null
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
