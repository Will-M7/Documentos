import DataObjectOutlined from '@mui/icons-material/DataObjectOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import HistoryOutlined from '@mui/icons-material/HistoryOutlined'
import HomeOutlined from '@mui/icons-material/HomeOutlined'
import LogoutOutlined from '@mui/icons-material/LogoutOutlined'
import ManageAccountsOutlined from '@mui/icons-material/ManageAccountsOutlined'
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import SnippetFolderOutlined from '@mui/icons-material/SnippetFolderOutlined'
import {
  AppBar,
  Box,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ListSubheader,
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

const ATENCION: Opcion[] = [
  { ruta: '/', texto: 'Inicio', icono: <HomeOutlined />, disponible: true },
  { ruta: '/documentos/nuevo', texto: 'Nuevo documento', icono: <DescriptionOutlined />, disponible: true },
  { ruta: '/historial', texto: 'Historial', icono: <HistoryOutlined />, disponible: true },
  { ruta: '/clientes', texto: 'Clientes', icono: <PeopleAltOutlined />, disponible: true },
]

/** Solo visibles para ADMIN. */
const ADMINISTRACION: Opcion[] = [
  { ruta: '/plantillas', texto: 'Plantillas', icono: <SnippetFolderOutlined />, disponible: true },
  { ruta: '/variables', texto: 'Variables', icono: <DataObjectOutlined />, disponible: true },
  { ruta: '/usuarios', texto: 'Usuarios', icono: <ManageAccountsOutlined />, disponible: false },
  { ruta: '/configuracion', texto: 'Configuración', icono: <SettingsOutlined />, disponible: true },
]

export function LayoutPrincipal() {
  const { sesion, cerrar } = useSesion()
  const esAdmin = sesion?.usuario.rol === 'ADMIN'

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
          {ATENCION.map((opcion) => (
            <OpcionMenu key={opcion.ruta} opcion={opcion} />
          ))}
          {esAdmin && (
            <>
              <ListSubheader>Administración</ListSubheader>
              {ADMINISTRACION.map((opcion) => (
                <OpcionMenu key={opcion.ruta} opcion={opcion} />
              ))}
            </>
          )}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}

function OpcionMenu({ opcion }: { opcion: Opcion }) {
  return (
    <ListItemButton
      component={NavLink}
      to={opcion.ruta}
      end={opcion.ruta === '/'}
      disabled={!opcion.disponible}
      sx={{ '&.active': { bgcolor: 'action.selected' } }}
    >
      <ListItemIcon>{opcion.icono}</ListItemIcon>
      <ListItemText primary={opcion.texto} />
    </ListItemButton>
  )
}
