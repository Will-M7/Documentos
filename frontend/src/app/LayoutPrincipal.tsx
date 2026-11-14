import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import HistoryOutlined from '@mui/icons-material/HistoryOutlined'
import HomeOutlined from '@mui/icons-material/HomeOutlined'
import LogoutOutlined from '@mui/icons-material/LogoutOutlined'
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined'
import {
  AppBar,
  Box,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material'
import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useSesion } from '../features/auth/sesionContext'

const ANCHO_MENU = 240

interface Opcion {
  ruta: string
  texto: string
  icono: ReactNode
  disponible: boolean
}

/** Las opciones marcadas como no disponibles se activan en la fase 1. */
const OPCIONES: Opcion[] = [
  { ruta: '/', texto: 'Inicio', icono: <HomeOutlined />, disponible: true },
  { ruta: '/documentos/nuevo', texto: 'Nuevo documento', icono: <DescriptionOutlined />, disponible: false },
  { ruta: '/historial', texto: 'Historial', icono: <HistoryOutlined />, disponible: false },
  { ruta: '/clientes', texto: 'Clientes', icono: <PeopleAltOutlined />, disponible: false },
]

export function LayoutPrincipal() {
  const { sesion, cerrar } = useSesion()

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Estudio Documental
          </Typography>
          <Typography sx={{ mr: 1 }}>{sesion?.usuario.nombre}</Typography>
          <Tooltip title="Cerrar sesión">
            <IconButton color="inherit" onClick={cerrar} aria-label="Cerrar sesión">
              <LogoutOutlined />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{ width: ANCHO_MENU, flexShrink: 0, '& .MuiDrawer-paper': { width: ANCHO_MENU } }}
      >
        <Toolbar />
        <List component="nav">
          {OPCIONES.map((opcion) => (
            <ListItemButton
              key={opcion.ruta}
              component={NavLink}
              to={opcion.ruta}
              end
              disabled={!opcion.disponible}
              sx={{ '&.active': { bgcolor: 'action.selected' } }}
            >
              <ListItemIcon>{opcion.icono}</ListItemIcon>
              <ListItemText primary={opcion.texto} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}
