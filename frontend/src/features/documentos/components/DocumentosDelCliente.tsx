import { Alert, Box, CircularProgress, List, ListItem, ListItemText, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { buscarDocumentos } from '../api'
import { AccionesDocumento } from './AccionesDocumento'

/** Documentos en los que participó un cliente, del más reciente al más antiguo. */
export function DocumentosDelCliente({ clienteId }: { clienteId: string }) {
  const consulta = useQuery({
    queryKey: ['documentos', 'cliente', clienteId],
    queryFn: () => buscarDocumentos({ q: '', desde: '', hasta: '', clienteId, pagina: 0, tamano: 50 }),
  })

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Documentos de este cliente
        {consulta.data ? ` (${consulta.data.totalElementos})` : ''}
      </Typography>
      {consulta.isPending && <CircularProgress size={20} />}
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      {consulta.data?.contenido.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          Todavía no participa en ningún documento.
        </Typography>
      )}
      <List dense disablePadding>
        {consulta.data?.contenido.map((d) => {
          const rol = d.participantes.find((p) => p.clienteId === clienteId)?.etiqueta
          return (
            <ListItem
              key={d.id}
              disableGutters
              secondaryAction={<AccionesDocumento documento={d} compacto />}
            >
              <ListItemText
                primary={`N.º ${d.numero} · ${d.titulo}`}
                secondary={`${new Date(d.creadoEn).toLocaleDateString('es-PE')}${rol ? ` · como ${rol}` : ''}`}
                sx={{ pr: 22 }}
              />
            </ListItem>
          )
        })}
      </List>
    </Box>
  )
}
