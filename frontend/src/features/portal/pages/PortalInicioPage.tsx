import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import {
  Alert,
  Card,
  CardActionArea,
  CardContent,
  CircularProgress,
  Grid,
  Stack,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { plantillasPublicas } from '../../solicitudes/api'

/** Lista de documentos que el cliente puede pedir. */
export function PortalInicioPage() {
  const navegar = useNavigate()
  const consulta = useQuery({ queryKey: ['portal', 'plantillas'], queryFn: plantillasPublicas })

  return (
    <>
      <Typography variant="h5" component="h1" gutterBottom>
        ¿Qué documento necesita?
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Elija el documento, complete sus datos y la oficina lo preparará. Recibirá un código para consultar
        cuándo está listo.
      </Typography>
      {consulta.isPending && <CircularProgress />}
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      {consulta.data?.length === 0 && (
        <Alert severity="info">Por ahora no hay documentos disponibles para pedir en línea.</Alert>
      )}
      <Grid container spacing={2}>
        {consulta.data?.map((p) => (
          <Grid key={p.id} size={{ xs: 12, sm: 6 }}>
            <Card sx={{ height: '100%' }}>
              <CardActionArea sx={{ height: '100%' }} onClick={() => navegar(`/portal/pedir/${p.id}`)}>
                <CardContent>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <DescriptionOutlined color="primary" />
                    <Typography variant="subtitle1">{p.nombre}</Typography>
                  </Stack>
                  {p.descripcion && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                      {p.descripcion}
                    </Typography>
                  )}
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </>
  )
}
