import { api, descargar } from '../../api/cliente'
import type { TipoVariable } from '../variables/api'

export type TipoParticipante = 'PERSONA' | 'EMPRESA' | 'CUALQUIERA'

export interface PlantillaResumen {
  id: string
  nombre: string
  descripcion: string | null
  categoria: string | null
  versionActual: number
  activo: boolean
  actualizadoEn: string
}

export interface Participante {
  clave: string
  etiqueta: string
  tipoCliente: TipoParticipante
  campos: { clave: string; etiqueta: string }[]
}

export interface VariablePlantilla {
  id: string
  clave: string
  etiqueta: string
  tipo: TipoVariable
  valorDefecto: string | null
  descripcion: string | null
}

export interface PlantillaDetalle extends Omit<PlantillaResumen, 'actualizadoEn'> {
  participantes: Participante[]
  variables: VariablePlantilla[]
  versiones: { numero: number; creadoEn: string; marcas: string[] }[]
}

export interface EdicionPlantilla {
  nombre: string
  descripcion: string
  categoria: string
  activo: boolean
  participantes: { clave: string; etiqueta: string; tipoCliente: TipoParticipante }[]
}

export const ETIQUETA_TIPO_PARTICIPANTE: Record<TipoParticipante, string> = {
  PERSONA: 'Persona',
  EMPRESA: 'Empresa',
  CUALQUIERA: 'Persona o empresa',
}

export function listarPlantillas(soloActivas = true) {
  return api<PlantillaResumen[]>(`/plantillas?soloActivas=${soloActivas}`)
}

export function obtenerPlantilla(id: string) {
  return api<PlantillaDetalle>(`/plantillas/${id}`)
}

export function subirPlantilla(datos: {
  archivo: File
  nombre: string
  categoria: string
  descripcion: string
}) {
  const formulario = new FormData()
  formulario.append('archivo', datos.archivo)
  formulario.append('nombre', datos.nombre)
  formulario.append('categoria', datos.categoria)
  formulario.append('descripcion', datos.descripcion)
  return api<PlantillaDetalle>('/plantillas', { method: 'POST', body: formulario })
}

export function subirVersion(id: string, archivo: File) {
  const formulario = new FormData()
  formulario.append('archivo', archivo)
  return api<PlantillaDetalle>(`/plantillas/${id}/versiones`, { method: 'POST', body: formulario })
}

export function editarPlantilla(id: string, datos: EdicionPlantilla) {
  return api<PlantillaDetalle>(`/plantillas/${id}`, { method: 'PUT', body: JSON.stringify(datos) })
}

export function descargarVersion(id: string, numero: number, nombre: string) {
  return descargar(`/plantillas/${id}/versiones/${numero}/docx`, `${nombre} v${numero}.docx`)
}
