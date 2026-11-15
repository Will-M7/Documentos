import type { DatosCliente } from '../clientes/api'

/** Datos vacíos de un participante en el formulario. */
export const PARTICIPANTE_VACIO: DatosCliente = {
  tipo: 'PERSONA',
  tipoDocumento: 'DNI',
  numeroDocumento: '',
  nombres: '',
  apellidoPaterno: '',
  apellidoMaterno: '',
  razonSocial: '',
  direccion: '',
  telefono: '',
  correo: '',
}

/** Convierte los null del backend en texto vacío para los campos del formulario. */
export function sinNulos(cliente: Partial<DatosCliente>): Partial<DatosCliente> {
  return Object.fromEntries(Object.entries(cliente).map(([k, v]) => [k, v ?? '']))
}
