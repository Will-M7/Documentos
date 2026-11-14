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

/** Cliente ya registrado con ese documento, o null si no existe en la base local. */
export async function clientePorDocumento(tipo: TipoDocumento, numero: string): Promise<Cliente | null> {
  try {
    return await api<Cliente>(`/clientes/documento/${tipo}/${encodeURIComponent(numero.trim())}`)
  } catch (error) {
    if (error instanceof Error && 'status' in error && (error as { status: number }).status === 404)
      return null
    throw error
  }
}

export interface ResultadoBusqueda {
  cliente: Cliente | null
  mensaje: string
  /** "CLIENTES", "Factiliza", "APIsPERU" o null. */
  origen: string | null
}

/**
 * DNI y RUC: Clientes primero y, si no está, el proveedor configurado (lo guarda en Clientes).
 * Otros documentos: solo Clientes.
 */
export async function buscarPorDocumento(
  tipo: TipoDocumento,
  numero: string,
  actualizar = false,
): Promise<ResultadoBusqueda> {
  if (tipo === 'DNI' || tipo === 'RUC') {
    const r = await api<{
      encontrado: boolean
      origen: string | null
      cliente: Cliente | null
      mensaje: string
    }>(`/consultas/${tipo}/${encodeURIComponent(numero.trim())}?actualizar=${actualizar}`, { method: 'POST' })
    return { cliente: r.cliente, mensaje: r.mensaje, origen: r.origen }
  }
  const cliente = await clientePorDocumento(tipo, numero)
  return cliente
    ? { cliente, mensaje: 'Datos cargados desde Clientes.', origen: 'CLIENTES' }
    : {
        cliente: null,
        mensaje: 'No está registrado. Complete los datos: se guardará en Clientes al generar.',
        origen: null,
      }
}
