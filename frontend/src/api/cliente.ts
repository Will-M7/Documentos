/**
 * Cliente HTTP único para hablar con el backend.
 *
 * - Agrega el token de acceso a cada petición y lo renueva solo cuando vence.
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
let obtenerTokenSesion: () => string | null = () => null
let alRenovar: (sesion: unknown) => void = () => {}
let alExpirarSesion: () => void = () => {}
let renovacionEnCurso: Promise<string | null> | null = null

/** Lo llama el proveedor de sesión para conectar la sesión con el cliente HTTP. */
export function configurarSesion(opciones: {
  obtenerToken: () => string | null
  obtenerTokenSesion: () => string | null
  alRenovar: (sesion: never) => void
  alExpirarSesion: () => void
}) {
  obtenerToken = opciones.obtenerToken
  obtenerTokenSesion = opciones.obtenerTokenSesion
  alRenovar = opciones.alRenovar as (sesion: unknown) => void
  alExpirarSesion = opciones.alExpirarSesion
}

/**
 * Pide un token de acceso nuevo con el token de sesión. Si varias peticiones vencen a la vez, se hace una sola
 * renovación y todas esperan la misma. Devuelve el token nuevo, o null si la sesión ya no sirve.
 */
function renovarAcceso(): Promise<string | null> {
  const tokenSesion = obtenerTokenSesion()
  if (!tokenSesion) return Promise.resolve(null)
  renovacionEnCurso ??= fetch('/api/v1/auth/renovar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tokenSesion }),
  })
    .then(async (r) => {
      if (!r.ok) return null
      const sesion = (await r.json()) as { token: string }
      alRenovar(sesion)
      return sesion.token
    })
    .catch(() => null)
    .finally(() => {
      renovacionEnCurso = null
    })
  return renovacionEnCurso
}

/** fetch con el token de acceso; si venció, lo renueva una vez y repite la petición. */
async function fetchAutenticado(ruta: string, opciones: RequestInit = {}): Promise<Response> {
  const enviar = (token: string | null) => {
    const cabeceras = new Headers(opciones.headers)
    if (opciones.body && !(opciones.body instanceof FormData)) {
      cabeceras.set('Content-Type', 'application/json')
    }
    if (token) cabeceras.set('Authorization', `Bearer ${token}`)
    return fetch(`/api/v1${ruta}`, { ...opciones, headers: cabeceras })
  }
  const token = obtenerToken()
  let respuesta = await enviar(token)
  if (respuesta.status === 401 && token) {
    const nuevo = await renovarAcceso()
    if (nuevo) {
      respuesta = await enviar(nuevo)
    }
    if (!nuevo || respuesta.status === 401) {
      alExpirarSesion()
    }
  }
  return respuesta
}

export async function api<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const respuesta = await fetchAutenticado(ruta, opciones)
  if (!respuesta.ok) {
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
  const respuesta = await fetchAutenticado(ruta)
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
