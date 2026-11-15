import { CircularProgress, CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { SesionProvider } from '../features/auth/SesionProvider'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { InicioPage } from '../features/inicio/pages/InicioPage'
import { LayoutPrincipal } from './LayoutPrincipal'
import { RutaProtegida } from './RutaProtegida'
import { RutaSoloAdmin } from './RutaSoloAdmin'
import { tema } from './tema'

// Cada pantalla se descarga recién cuando se abre: la primera carga es más rápida.
const NuevoDocumentoPage = lazy(() =>
  import('../features/documentos/pages/NuevoDocumentoPage').then((m) => ({ default: m.NuevoDocumentoPage })),
)
const HistorialPage = lazy(() =>
  import('../features/documentos/pages/HistorialPage').then((m) => ({ default: m.HistorialPage })),
)
const ClientesPage = lazy(() =>
  import('../features/clientes/pages/ClientesPage').then((m) => ({ default: m.ClientesPage })),
)
const PlantillasPage = lazy(() =>
  import('../features/plantillas/pages/PlantillasPage').then((m) => ({ default: m.PlantillasPage })),
)
const PlantillaDetallePage = lazy(() =>
  import('../features/plantillas/pages/PlantillaDetallePage').then((m) => ({
    default: m.PlantillaDetallePage,
  })),
)
const VariablesPage = lazy(() =>
  import('../features/variables/pages/VariablesPage').then((m) => ({ default: m.VariablesPage })),
)
const UsuariosPage = lazy(() =>
  import('../features/usuarios/pages/UsuariosPage').then((m) => ({ default: m.UsuariosPage })),
)
const ConfiguracionPage = lazy(() =>
  import('../features/configuracion/pages/ConfiguracionPage').then((m) => ({ default: m.ConfiguracionPage })),
)
const AuditoriaPage = lazy(() =>
  import('../features/auditoria/pages/AuditoriaPage').then((m) => ({ default: m.AuditoriaPage })),
)

const SolicitudesPage = lazy(() =>
  import('../features/solicitudes/pages/SolicitudesPage').then((m) => ({ default: m.SolicitudesPage })),
)
const PortalLayout = lazy(() =>
  import('../features/portal/PortalLayout').then((m) => ({ default: m.PortalLayout })),
)
const PortalInicioPage = lazy(() =>
  import('../features/portal/pages/PortalInicioPage').then((m) => ({ default: m.PortalInicioPage })),
)
const PortalPedirPage = lazy(() =>
  import('../features/portal/pages/PortalPedirPage').then((m) => ({ default: m.PortalPedirPage })),
)
const PortalEstadoPage = lazy(() =>
  import('../features/portal/pages/PortalEstadoPage').then((m) => ({ default: m.PortalEstadoPage })),
)

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

/** Raíz de la aplicación: proveedores globales y rutas. */
export function App() {
  return (
    <ThemeProvider theme={tema}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <SesionProvider>
          <BrowserRouter>
            <Suspense fallback={<CircularProgress sx={{ m: 4 }} />}>
              <Routes>
                <Route path="/login" element={<LoginPage />} />
                {/* Portal público: los clientes piden documentos sin usuario */}
                <Route path="/portal" element={<PortalLayout />}>
                  <Route index element={<PortalInicioPage />} />
                  <Route path="pedir/:id" element={<PortalPedirPage />} />
                  <Route path="estado" element={<PortalEstadoPage />} />
                </Route>
                <Route element={<RutaProtegida />}>
                  <Route element={<LayoutPrincipal />}>
                    <Route index element={<InicioPage />} />
                    <Route path="documentos/nuevo" element={<NuevoDocumentoPage />} />
                    <Route path="historial" element={<HistorialPage />} />
                    <Route path="clientes" element={<ClientesPage />} />
                    <Route path="solicitudes" element={<SolicitudesPage />} />
                    <Route element={<RutaSoloAdmin />}>
                      <Route path="plantillas" element={<PlantillasPage />} />
                      <Route path="plantillas/:id" element={<PlantillaDetallePage />} />
                      <Route path="variables" element={<VariablesPage />} />
                      <Route path="usuarios" element={<UsuariosPage />} />
                      <Route path="configuracion" element={<ConfiguracionPage />} />
                      <Route path="auditoria" element={<AuditoriaPage />} />
                    </Route>
                  </Route>
                </Route>
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </Suspense>
          </BrowserRouter>
        </SesionProvider>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
