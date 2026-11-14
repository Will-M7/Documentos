import { Navigate, Outlet, useLocation } from 'react-router'
import { useSesion } from '../features/auth/sesionContext'

/** Deja pasar solo con sesión iniciada; si no, envía al login y recuerda a dónde se quería ir. */
export function RutaProtegida() {
  const { sesion } = useSesion()
  const ubicacion = useLocation()

  if (!sesion) {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  }
  return <Outlet />
}
