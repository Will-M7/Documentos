import { api } from '../../api/cliente'

export type Proveedor = 'DECOLECTA' | 'APISPERU' | 'FACTILIZA'

export interface Credencial {
  id: string
  proveedor: Proveedor
  nombre: string
  tokenFinal: string
  prioridad: number
  activo: boolean
  ultimoEstado: string | null
  ultimoUso: string | null
  /** Consultas que permite el plan por mes; null = sin límite. */
  limiteMensual: number | null
  usadasEsteMes: number
  /** Si el proveedor dijo que se acabó el cupo, hasta cuándo no se usa. */
  agotadaHasta: string | null
}

export const NOMBRE_PROVEEDOR: Record<Proveedor, string> = {
  DECOLECTA: 'Decolecta',
  APISPERU: 'APIsPERU',
  FACTILIZA: 'Factiliza',
}

/** Cómo conseguir el token de cada proveedor y cuántas consultas gratis da. */
export const AYUDA_PROVEEDOR: Record<
  Proveedor,
  { texto: string; enlace: string; limiteSugerido: number | null }
> = {
  DECOLECTA: {
    texto: 'Gratis: 100 consultas al mes. Regístrese y copie su token en el perfil.',
    enlace: 'https://decolecta.com/profile',
    limiteSugerido: 100,
  },
  APISPERU: {
    texto: 'Plan gratuito con registro. Copie el token que le muestran al registrarse.',
    enlace: 'https://apisperu.com',
    limiteSugerido: null,
  },
  FACTILIZA: {
    texto: 'De pago. Use el token de su cuenta.',
    enlace: 'https://factiliza.com',
    limiteSugerido: null,
  },
}

export function listarCredenciales() {
  return api<{ consultasDelMes: number; credenciales: Credencial[] }>('/credenciales')
}

export function crearCredencial(datos: {
  proveedor: Proveedor
  nombre: string
  token: string
  prioridad: number
  limiteMensual: number | null
}) {
  return api<Credencial>('/credenciales', { method: 'POST', body: JSON.stringify(datos) })
}

export function editarCredencial(
  id: string,
  datos: { nombre: string; token: string; prioridad: number; activo: boolean; limiteMensual: number | null },
) {
  return api<Credencial>(`/credenciales/${id}`, { method: 'PUT', body: JSON.stringify(datos) })
}

export function eliminarCredencial(id: string) {
  return api<void>(`/credenciales/${id}`, { method: 'DELETE' })
}

export function estadoPdf() {
  return api<{ disponible: boolean; mensaje: string }>('/documentos/estado-pdf')
}
