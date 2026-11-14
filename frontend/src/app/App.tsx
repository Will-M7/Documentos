import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { SesionProvider } from '../features/auth/SesionProvider'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { ClientesPage } from '../features/clientes/pages/ClientesPage'
import { InicioPage } from '../features/inicio/pages/InicioPage'
import { PlantillaDetallePage } from '../features/plantillas/pages/PlantillaDetallePage'
import { PlantillasPage } from '../features/plantillas/pages/PlantillasPage'
import { VariablesPage } from '../features/variables/pages/VariablesPage'
import { LayoutPrincipal } from './LayoutPrincipal'
import { RutaProtegida } from './RutaProtegida'
import { RutaSoloAdmin } from './RutaSoloAdmin'
import { tema } from './tema'

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
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route element={<RutaProtegida />}>
                <Route element={<LayoutPrincipal />}>
                  <Route index element={<InicioPage />} />
                  <Route path="clientes" element={<ClientesPage />} />
                  <Route element={<RutaSoloAdmin />}>
                    <Route path="plantillas" element={<PlantillasPage />} />
                    <Route path="plantillas/:id" element={<PlantillaDetallePage />} />
                    <Route path="variables" element={<VariablesPage />} />
                  </Route>
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </SesionProvider>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
