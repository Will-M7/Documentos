import { api } from '../../api/cliente'

export type Proveedor = 'FACTILIZA' | 'APISPERU'

export interface Credencial {
  id: string
  proveedor: Proveedor
  nombre: string
  tokenFinal: string
  prioridad: number
  activo: boolean
  ultimoEstado: string | null
  ultimoUso: string | null
}

export const NOMBRE_PROVEEDOR: Record<Proveedor, string> = {
  FACTILIZA: 'Factiliza',
  APISPERU: 'APIsPERU',
}

export function listarCredenciales() {
  return api<{ consultasDelMes: number; credenciales: Credencial[] }>('/credenciales')
}

export function crearCredencial(datos: {
  proveedor: Proveedor
  nombre: string
  token: string
  prioridad: number
}) {
  return api<Credencial>('/credenciales', { method: 'POST', body: JSON.stringify(datos) })
}

export function editarCredencial(
  id: string,
  datos: { nombre: string; token: string; prioridad: number; activo: boolean },
) {
  return api<Credencial>(`/credenciales/${id}`, { method: 'PUT', body: JSON.stringify(datos) })
}

export function eliminarCredencial(id: string) {
  return api<void>(`/credenciales/${id}`, { method: 'DELETE' })
}

export function estadoPdf() {
  return api<{ disponible: boolean; mensaje: string }>('/documentos/estado-pdf')
}
