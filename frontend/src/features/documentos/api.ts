import { api, descargar, obtenerArchivo } from '../../api/cliente'
import type { Pagina } from '../../api/tipos'
import type { Cliente, DatosCliente, TipoDocumento } from '../clientes/api'

export interface ParticipanteDocumento {
  participante: string
  etiqueta: string
  clienteId: string
  nombre: string
  documentoIdentidad: string
}

export interface DocumentoResumen {
  id: string
  numero: number
  titulo: string
  plantillaId: string
  plantillaNombre: string
  usuarioNombre: string
  creadoEn: string
  tienePdf: boolean
  participantes: ParticipanteDocumento[]
}

export interface DocumentoDetalle {
  resumen: DocumentoResumen
  variables: Record<string, string>
  marcas: Record<string, string>
  avisoPdf: string | null
}

export interface PedidoDocumento {
  plantillaId: string
  titulo: string
  variables: Record<string, string>
  participantes: Record<string, DatosCliente>
}

export function generarDocumento(pedido: PedidoDocumento) {
  return api<DocumentoDetalle>('/documentos', { method: 'POST', body: JSON.stringify(pedido) })
}

export function buscarDocumentos(filtro: {
  q: string
  desde: string
  hasta: string
  pagina: number
  tamano: number
}) {
  const params = new URLSearchParams({
    q: filtro.q,
    pagina: String(filtro.pagina),
    tamano: String(filtro.tamano),
  })
  if (filtro.desde) params.set('desde', filtro.desde)
  if (filtro.hasta) params.set('hasta', filtro.hasta)
  return api<Pagina<DocumentoResumen>>(`/documentos?${params}`)
}

export function obtenerDocumento(id: string) {
  return api<DocumentoDetalle>(`/documentos/${id}`)
}

export function descargarDocx(d: DocumentoResumen) {
  return descargar(`/documentos/${d.id}/docx`, `${d.titulo}.docx`)
}

export function descargarPdf(d: DocumentoResumen) {
  return descargar(`/documentos/${d.id}/pdf`, `${d.titulo}.pdf`)
}

/** Abre el PDF en una pestaña nueva para verlo o imprimirlo. */
export async function verPdf(d: DocumentoResumen) {
  const { contenido } = await obtenerArchivo(`/documentos/${d.id}/pdf?verEnLinea=true`)
  window.open(URL.createObjectURL(contenido), '_blank')
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

export function obtenerCliente(id: string) {
  return api<Cliente>(`/clientes/${id}`)
}
