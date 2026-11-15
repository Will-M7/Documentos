import { api } from '../../api/cliente'

export interface ResultadoImportacion {
  clientesNuevos: number
  clientesCompletados: number
  clientesOmitidos: number
  plantillasCreadas: number
  plantillasOmitidas: number
  avisos: string[]
}

/** Sube el ZIP exportado desde la aplicación de escritorio (v1). */
export function importarV1(archivo: File) {
  const formulario = new FormData()
  formulario.append('archivo', archivo)
  return api<ResultadoImportacion>('/importacion/v1', { method: 'POST', body: formulario })
}
