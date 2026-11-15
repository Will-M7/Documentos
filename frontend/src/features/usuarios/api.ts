import { api } from '../../api/cliente'
import type { Rol } from '../auth/api'

export interface UsuarioAdmin {
  id: string
  usuario: string
  nombre: string
  rol: Rol
  activo: boolean
  /** Fecha hasta la que está bloqueado por intentos fallidos, o null. */
  bloqueadoHasta: string | null
  creadoEn: string
}

export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  OPERADOR: 'Operador',
}

export function listarUsuarios() {
  return api<UsuarioAdmin[]>('/usuarios')
}

export function crearUsuario(datos: { usuario: string; nombre: string; clave: string; rol: Rol }) {
  return api<UsuarioAdmin>('/usuarios', { method: 'POST', body: JSON.stringify(datos) })
}

export function editarUsuario(id: string, datos: { nombre: string; rol: Rol; activo: boolean }) {
  return api<UsuarioAdmin>(`/usuarios/${id}`, { method: 'PUT', body: JSON.stringify(datos) })
}

export function restablecerClave(id: string, clave: string) {
  return api<void>(`/usuarios/${id}/clave`, { method: 'PUT', body: JSON.stringify({ clave }) })
}

export function cambiarMiClave(actual: string, nueva: string) {
  return api<void>('/auth/clave', { method: 'PUT', body: JSON.stringify({ actual, nueva }) })
}
