import { api } from '../../api/cliente'
import type { Pagina } from '../../api/tipos'

export type TipoCliente = 'PERSONA' | 'EMPRESA'
export type TipoDocumento = 'DNI' | 'RUC' | 'CE' | 'PASAPORTE'
export type OrigenDatos = 'MANUAL' | 'FACTILIZA' | 'APISPERU'

export interface DatosCliente {
  tipo: TipoCliente
  tipoDocumento: TipoDocumento
  numeroDocumento: string
  nombres?: string | null
  apellidoPaterno?: string | null
  apellidoMaterno?: string | null
  razonSocial?: string | null
  direccion?: string | null
  telefono?: string | null
  correo?: string | null
}

export interface Cliente extends DatosCliente {
  id: string
  nombreCompleto: string
  origen: OrigenDatos
  actualizadoEn: string
}

export const ETIQUETA_DOCUMENTO: Record<TipoDocumento, string> = {
  DNI: 'DNI',
  RUC: 'RUC',
  CE: 'Carné de extranjería',
  PASAPORTE: 'Pasaporte',
}

export function buscarClientes(q: string, pagina: number, tamano: number) {
  const params = new URLSearchParams({ q, pagina: String(pagina), tamano: String(tamano) })
  return api<Pagina<Cliente>>(`/clientes?${params}`)
}

export function crearCliente(datos: DatosCliente) {
  return api<Cliente>('/clientes', { method: 'POST', body: JSON.stringify(datos) })
}

export function actualizarCliente(id: string, datos: DatosCliente) {
  return api<Cliente>(`/clientes/${id}`, { method: 'PUT', body: JSON.stringify(datos) })
}

export function darDeBajaCliente(id: string) {
  return api<void>(`/clientes/${id}`, { method: 'DELETE' })
}
