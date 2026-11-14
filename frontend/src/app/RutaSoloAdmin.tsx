import { Navigate, Outlet } from 'react-router'
import { useSesion } from '../features/auth/sesionContext'

/** Pantallas de administración: un OPERADOR que entre por la URL vuelve al inicio. */
export function RutaSoloAdmin() {
  const { sesion } = useSesion()
  return sesion?.usuario.rol === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />
}
