import { api } from '../../api/cliente'

export type Rol = 'ADMIN' | 'OPERADOR'

export interface Usuario {
  id: string
  usuario: string
  nombre: string
  rol: Rol
}

export interface Sesion {
  /** Token de acceso: dura pocos minutos y se renueva solo. */
  token: string
  expiraEn: string
  /** Token para renovar el acceso sin contraseña. */
  tokenSesion: string
  /** Hasta cuándo se puede renovar; después hay que ingresar de nuevo. */
  sesionExpiraEn: string
  usuario: Usuario
}

export function iniciarSesion(usuario: string, clave: string) {
  return api<Sesion>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ usuario, clave }),
  })
}

/** Avisa al servidor que cierre la sesión de este navegador. No espera la respuesta. */
export function cerrarSesionEnServidor(tokenSesion: string) {
  fetch('/api/v1/auth/salir', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tokenSesion }),
  }).catch(() => {})
}
