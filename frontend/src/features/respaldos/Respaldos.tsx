import BackupOutlined from '@mui/icons-material/BackupOutlined'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link as RouterLink } from 'react-router'
import { estadoRespaldos, respaldarAhora, tamanoLegible } from './api'

const fecha = (iso: string) =>
  new Date(iso).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

/** Tarjeta de Configuración: últimos respaldos y botón para respaldar al momento. */
export function Respaldos() {
  const queryClient = useQueryClient()
  const estado = useQuery({ queryKey: ['respaldos'], queryFn: estadoRespaldos })
  const respaldar = useMutation({
    mutationFn: respaldarAhora,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['respaldos'] }),
  })
  const e = estado.data

  return (
    <Card sx={{ mt: 2 }}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', mb: 1 }}>
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6">Respaldos</Typography>
            <Typography variant="body2" color="text.secondary">
              Copia de la base de datos y de los Word/PDF. Copie la carpeta de respaldos fuera de esta PC al
              menos una vez por semana.
            </Typography>
          </Box>
          {e?.puedeRespaldarAhora && (
            <Button
              variant="contained"
              startIcon={<BackupOutlined />}
              loading={respaldar.isPending}
              onClick={() => respaldar.mutate()}
            >
              Respaldar ahora
            </Button>
          )}
        </Box>

        {estado.error && <Alert severity="error">{estado.error.message}</Alert>}
        {e && !e.configurado && (
          <Alert severity="info">
            En este equipo no hay carpeta de respaldos configurada. En la PC servidor se configura con{' '}
            <code>RESPALDOS_DIR</code> en <code>config.cmd</code> (ver la guía de instalación).
          </Alert>
        )}
        {e?.configurado && (
          <Alert severity={e.alDia ? 'success' : 'warning'} sx={{ mb: 2 }}>
            {e.ultimo
              ? `Último respaldo completo: ${fecha(e.ultimo)}.`
              : 'Todavía no hay ningún respaldo completo.'}
            {!e.alDia && ' Hace más de 2 días que no se respalda: revise el Programador de tareas.'}
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              Carpeta: <code>{e.directorio}</code>
            </Typography>
          </Alert>
        )}

        {respaldar.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {respaldar.error.message}
          </Alert>
        )}
        {respaldar.data && (
          <Alert severity={respaldar.data.exito ? 'success' : 'error'} sx={{ mb: 2 }}>
            {respaldar.data.exito ? 'Respaldo terminado.' : 'El respaldo falló.'}
            {respaldar.data.salida && (
              <Box component="pre" sx={{ m: 0, mt: 1, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                {respaldar.data.salida}
              </Box>
            )}
          </Alert>
        )}

        {e && e.respaldos.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Fecha</TableCell>
                <TableCell>Carpeta</TableCell>
                <TableCell align="right">Tamaño</TableCell>
                <TableCell>Estado</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {e.respaldos.map((r) => (
                <TableRow key={r.nombre}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{fecha(r.fecha)}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{r.nombre}</TableCell>
                  <TableCell align="right">{tamanoLegible(r.tamanoBytes)}</TableCell>
                  <TableCell>
                    {r.completo ? (
                      <Chip size="small" color="success" label="Completo" />
                    ) : (
                      <Chip size="small" color="error" label="Incompleto" />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

/** Aviso en Inicio para el administrador cuando los respaldos están atrasados. */
export function AvisoRespaldos() {
  const estado = useQuery({ queryKey: ['respaldos'], queryFn: estadoRespaldos })
  const e = estado.data
  if (!e?.configurado || e.alDia) return null
  return (
    <Alert
      severity="warning"
      sx={{ mb: 2 }}
      action={
        <Button color="inherit" size="small" component={RouterLink} to="/configuracion">
          Ver respaldos
        </Button>
      }
    >
      {e.ultimo
        ? `El último respaldo es del ${fecha(e.ultimo)}. Hace más de 2 días que no se respalda.`
        : 'Todavía no se ha hecho ningún respaldo de la información.'}
    </Alert>
  )
}
