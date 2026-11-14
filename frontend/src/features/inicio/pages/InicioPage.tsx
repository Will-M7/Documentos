import Add from '@mui/icons-material/Add'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Grid,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { Link as RouterLink } from 'react-router'
import { useSesion } from '../../auth/sesionContext'
import { buscarDocumentos } from '../../documentos/api'
import { AccionesDocumento } from '../../documentos/components/AccionesDocumento'

/** Accesos rápidos y últimos documentos. */
export function InicioPage() {
  const { sesion } = useSesion()
  const recientes = useQuery({
    queryKey: ['documentos', 'recientes'],
    queryFn: () => buscarDocumentos({ q: '', desde: '', hasta: '', pagina: 0, tamano: 5 }),
  })

  return (
    <>
      <Typography variant="h5" component="h1" gutterBottom>
        Hola, {sesion?.usuario.nombre}
      </Typography>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
                <Typography variant="h6">Nuevo documento</Typography>
                <Typography color="text.secondary">
                  Elija una plantilla, busque a las personas por su DNI o RUC y genere el documento.
                </Typography>
                <Button variant="contained" startIcon={<Add />} component={RouterLink} to="/documentos/nuevo">
                  Empezar
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">Últimos documentos</Typography>
              {recientes.error && <Alert severity="error">{recientes.error.message}</Alert>}
              {recientes.data?.contenido.length === 0 && (
                <Typography color="text.secondary">Todavía no hay documentos generados.</Typography>
              )}
              <List dense>
                {recientes.data?.contenido.map((d) => (
                  <ListItem
                    key={d.id}
                    disableGutters
                    secondaryAction={<AccionesDocumento documento={d} compacto />}
                  >
                    <ListItemText
                      primary={`N.º ${d.numero} · ${d.titulo}`}
                      secondary={new Date(d.creadoEn).toLocaleString('es-PE')}
                      sx={{ pr: 22 }}
                    />
                  </ListItem>
                ))}
              </List>
              {!!recientes.data?.totalElementos && (
                <Button component={RouterLink} to="/historial">
                  Ver todo el historial
                </Button>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  )
}
