import { api } from '../../api/cliente'

export type Rol = 'ADMIN' | 'OPERADOR'

export interface Usuario {
  id: string
  usuario: string
  nombre: string
  rol: Rol
}

export interface Sesion {
  token: string
  expiraEn: string
  usuario: Usuario
}

export function iniciarSesion(usuario: string, clave: string) {
  return api<Sesion>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ usuario, clave }),
  })
}
