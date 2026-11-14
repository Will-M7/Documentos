import { api } from '../../api/cliente'

export type TipoVariable = 'TEXTO' | 'TEXTO_LARGO' | 'NUMERO' | 'FECHA' | 'MONEDA'

export interface Variable {
  id: string
  clave: string
  etiqueta: string
  tipo: TipoVariable
  valorDefecto: string | null
  descripcion: string | null
}

export type DatosVariable = Omit<Variable, 'id'>

export const ETIQUETA_TIPO: Record<TipoVariable, string> = {
  TEXTO: 'Texto corto',
  TEXTO_LARGO: 'Texto largo',
  NUMERO: 'Número',
  FECHA: 'Fecha',
  MONEDA: 'Monto (S/)',
}

export function listarVariables() {
  return api<Variable[]>('/variables')
}

export function crearVariable(datos: DatosVariable) {
  return api<Variable>('/variables', { method: 'POST', body: JSON.stringify(datos) })
}

export function editarVariable(id: string, datos: Omit<DatosVariable, 'clave'>) {
  return api<Variable>(`/variables/${id}`, { method: 'PUT', body: JSON.stringify(datos) })
}
