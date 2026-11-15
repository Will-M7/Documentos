import { api } from '../../api/cliente'
import type { Pagina } from '../../api/tipos'
import type { DatosCliente } from '../clientes/api'
import type { Participante, VariablePlantilla } from '../plantillas/api'

export type EstadoSolicitud = 'PENDIENTE' | 'ATENDIDA' | 'RECHAZADA'

export interface SolicitudResumen {
  id: string
  codigo: string
  plantillaId: string
  plantillaNombre: string
  estado: EstadoSolicitud
  contactoNombre: string
  contactoTelefono: string | null
  contactoCorreo: string | null
  motivoRechazo: string | null
  documentoId: string | null
  creadoEn: string
  atendidaEn: string | null
}

export interface SolicitudDetalle {
  resumen: SolicitudResumen
  datos: { variables: Record<string, string>; participantes: Record<string, DatosCliente> }
}

export const ETIQUETA_ESTADO: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  ATENDIDA: 'Atendida',
  RECHAZADA: 'Rechazada',
}

// --- Oficina (con usuario) ---

export function listarSolicitudes(estado: EstadoSolicitud | '', pagina: number, tamano: number) {
  const params = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) })
  if (estado) params.set('estado', estado)
  return api<Pagina<SolicitudResumen>>(`/solicitudes?${params}`)
}

export function contarPendientes() {
  return api<{ pendientes: number }>('/solicitudes/pendientes')
}

export function obtenerSolicitud(id: string) {
  return api<SolicitudDetalle>(`/solicitudes/${id}`)
}

export function rechazarSolicitud(id: string, motivo: string) {
  return api<SolicitudResumen>(`/solicitudes/${id}/rechazar`, {
    method: 'POST',
    body: JSON.stringify({ motivo }),
  })
}

// --- Portal público (sin usuario) ---

export interface PlantillaPublica {
  id: string
  nombre: string
  descripcion: string | null
  categoria: string | null
  participantes: Participante[]
  variables: VariablePlantilla[]
}

export interface EnvioSolicitud {
  plantillaId: string
  variables: Record<string, string>
  participantes: Record<string, DatosCliente>
  contactoNombre: string
  contactoTelefono: string
  contactoCorreo: string
  aceptaDatos: boolean
}

export function plantillasPublicas() {
  return api<PlantillaPublica[]>('/publico/plantillas')
}

export function plantillaPublica(id: string) {
  return api<PlantillaPublica>(`/publico/plantillas/${id}`)
}

export function enviarSolicitud(envio: EnvioSolicitud) {
  return api<{ codigo: string; mensaje: string }>('/publico/solicitudes', {
    method: 'POST',
    body: JSON.stringify({
      ...envio,
      // Un correo vacío no es "inválido": no se envía
      contactoCorreo: envio.contactoCorreo.trim() || null,
    }),
  })
}

export function estadoPublico(codigo: string) {
  return api<{
    codigo: string
    documento: string
    estado: EstadoSolicitud
    mensaje: string
    enviadaEn: string
  }>(`/publico/solicitudes/${encodeURIComponent(codigo.trim())}`)
}
