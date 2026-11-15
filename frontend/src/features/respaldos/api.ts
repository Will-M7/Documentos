import { api } from '../../api/cliente'

export interface Respaldo {
  nombre: string
  fecha: string
  tamanoBytes: number
  completo: boolean
}

export interface EstadoRespaldos {
  configurado: boolean
  directorio: string | null
  puedeRespaldarAhora: boolean
  alDia: boolean
  ultimo: string | null
  respaldos: Respaldo[]
}

export function estadoRespaldos() {
  return api<EstadoRespaldos>('/respaldos')
}

export function respaldarAhora() {
  return api<{ exito: boolean; salida: string }>('/respaldos', { method: 'POST' })
}

export function tamanoLegible(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}
