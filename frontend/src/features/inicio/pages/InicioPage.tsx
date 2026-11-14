import { Card, CardContent, Grid, Typography } from '@mui/material'
import { useSesion } from '../../auth/sesionContext'

/** Pantalla inicial. En la fase 1 mostrará accesos rápidos y los últimos documentos. */
export function InicioPage() {
  const { sesion } = useSesion()

  return (
    <>
      <Typography variant="h5" component="h1" gutterBottom>
        Hola, {sesion?.usuario.nombre}
      </Typography>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6">Nuevo documento</Typography>
              <Typography color="text.secondary">Disponible en la fase 1.</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6">Últimos documentos</Typography>
              <Typography color="text.secondary">Todavía no hay documentos generados.</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  )
}
