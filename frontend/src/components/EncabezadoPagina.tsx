import { Box, Typography } from '@mui/material'
import type { ReactNode } from 'react'

/** Título de cada pantalla con sus acciones a la derecha. */
export function EncabezadoPagina({
  titulo,
  descripcion,
  acciones,
}: {
  titulo: string
  descripcion?: string
  acciones?: ReactNode
}) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, mb: 3, flexWrap: 'wrap' }}>
      <Box sx={{ flexGrow: 1 }}>
        <Typography variant="h5" component="h1">
          {titulo}
        </Typography>
        {descripcion && <Typography color="text.secondary">{descripcion}</Typography>}
      </Box>
      {acciones}
    </Box>
  )
}
