import { createTheme } from '@mui/material/styles'
import { esES } from '@mui/material/locale'

/** Tema visual único de la aplicación. Cambiar colores solo aquí. */
export const tema = createTheme(
  {
    palette: {
      primary: { main: '#1f4e79' },
      secondary: { main: '#8a6d3b' },
      background: { default: '#f5f6f8' },
    },
    shape: { borderRadius: 8 },
    typography: {
      fontFamily: '"Inter", "Segoe UI", Roboto, Arial, sans-serif',
    },
  },
  esES,
)
