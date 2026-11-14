import ArrowBack from '@mui/icons-material/ArrowBack'
import Download from '@mui/icons-material/Download'
import UploadFile from '@mui/icons-material/UploadFile'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControlLabel,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import { ETIQUETA_TIPO } from '../../variables/api'
import {
  descargarVersion,
  editarPlantilla,
  ETIQUETA_TIPO_PARTICIPANTE,
  obtenerPlantilla,
  subirVersion,
  type EdicionPlantilla,
  type PlantillaDetalle,
  type TipoParticipante,
} from '../api'
import { GuiaMarcas } from '../components/GuiaMarcas'

export function PlantillaDetallePage() {
  const { id = '' } = useParams()
  const consulta = useQuery({ queryKey: ['plantilla', id], queryFn: () => obtenerPlantilla(id) })

  if (consulta.isPending) return <CircularProgress />
  if (consulta.error) return <Alert severity="error">{consulta.error.message}</Alert>
  // La clave reinicia el formulario cuando llega una versión nueva de la plantilla
  return <Detalle key={`${consulta.data.id}-${consulta.data.versionActual}`} plantilla={consulta.data} />
}

function Detalle({ plantilla }: { plantilla: PlantillaDetalle }) {
  const queryClient = useQueryClient()
  const [edicion, setEdicion] = useState<EdicionPlantilla>(() => aEdicion(plantilla))
  const [guardado, setGuardado] = useState(false)

  const alActualizar = (nueva: PlantillaDetalle) => {
    queryClient.setQueryData(['plantilla', nueva.id], nueva)
    queryClient.invalidateQueries({ queryKey: ['plantillas'] })
  }
  const guardar = useMutation({
    mutationFn: () => editarPlantilla(plantilla.id, edicion),
    onSuccess: (nueva) => {
      alActualizar(nueva)
      setGuardado(true)
    },
  })
  const nuevaVersion = useMutation({
    mutationFn: (archivo: File) => subirVersion(plantilla.id, archivo),
    onSuccess: alActualizar,
  })

  const cambiarParticipante = (
    clave: string,
    cambios: Partial<{ etiqueta: string; tipoCliente: TipoParticipante }>,
  ) =>
    setEdicion((e) => ({
      ...e,
      participantes: e.participantes.map((p) => (p.clave === clave ? { ...p, ...cambios } : p)),
    }))

  return (
    <>
      <Button component={RouterLink} to="/plantillas" startIcon={<ArrowBack />} sx={{ mb: 2 }}>
        Plantillas
      </Button>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <Typography variant="h5" component="h1" sx={{ flexGrow: 1 }}>
          {plantilla.nombre} <Chip label={`v${plantilla.versionActual}`} size="small" />
        </Typography>
        <Button
          variant="outlined"
          component="label"
          startIcon={<UploadFile />}
          loading={nuevaVersion.isPending}
        >
          Subir nueva versión
          <input
            hidden
            type="file"
            accept=".docx"
            onChange={(e) => {
              const archivo = e.target.files?.[0]
              if (archivo) nuevaVersion.mutate(archivo)
              e.target.value = ''
            }}
          />
        </Button>
        <Button
          variant="contained"
          loading={guardar.isPending}
          onClick={() => {
            setGuardado(false)
            guardar.mutate()
          }}
        >
          Guardar cambios
        </Button>
      </Box>

      {nuevaVersion.error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {nuevaVersion.error.message}
        </Alert>
      )}
      {nuevaVersion.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Versión {plantilla.versionActual} subida. Los documentos anteriores conservan la versión con la que
          se generaron.
        </Alert>
      )}
      {guardar.error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {guardar.error.message}
        </Alert>
      )}
      {guardado && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setGuardado(false)}>
          Cambios guardados.
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 5 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Datos generales
              </Typography>
              <Stack spacing={2}>
                <TextField
                  label="Nombre"
                  value={edicion.nombre}
                  onChange={(e) => setEdicion({ ...edicion, nombre: e.target.value })}
                />
                <TextField
                  label="Categoría"
                  value={edicion.categoria}
                  onChange={(e) => setEdicion({ ...edicion, categoria: e.target.value })}
                />
                <TextField
                  label="Descripción"
                  multiline
                  minRows={2}
                  value={edicion.descripcion}
                  onChange={(e) => setEdicion({ ...edicion, descripcion: e.target.value })}
                />
                <FormControlLabel
                  control={
                    <Switch
                      checked={edicion.activo}
                      onChange={(e) => setEdicion({ ...edicion, activo: e.target.checked })}
                    />
                  }
                  label={edicion.activo ? 'Activa: se puede usar para generar' : 'Desactivada'}
                />
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6">Versiones</Typography>
              <List dense>
                {plantilla.versiones.map((v) => (
                  <ListItem
                    key={v.numero}
                    secondaryAction={
                      <Tooltip title="Descargar este Word">
                        <IconButton
                          edge="end"
                          aria-label={`Descargar versión ${v.numero}`}
                          onClick={() => descargarVersion(plantilla.id, v.numero, plantilla.nombre)}
                        >
                          <Download />
                        </IconButton>
                      </Tooltip>
                    }
                  >
                    <ListItemText
                      primary={`Versión ${v.numero}${v.numero === plantilla.versionActual ? ' (actual)' : ''}`}
                      secondary={`${new Date(v.creadoEn).toLocaleString('es-PE')} · ${v.marcas.length} marcas`}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 7 }}>
          <Card>
            <CardContent>
              <Typography variant="h6">Participantes</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Personas o empresas que aparecen en el documento. Ponga un nombre claro: es lo que verá quien
                genere el documento.
              </Typography>
              {plantilla.participantes.length === 0 && (
                <Typography color="text.secondary">Esta plantilla no usa datos de clientes.</Typography>
              )}
              <Stack spacing={2}>
                {edicion.participantes.map((p) => {
                  const original = plantilla.participantes.find((o) => o.clave === p.clave)
                  return (
                    <Box key={p.clave} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 2 }}>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', mb: 1 }}>
                        {`{{${p.clave}.…}}`}
                      </Typography>
                      <Grid container spacing={2}>
                        <Grid size={{ xs: 12, sm: 7 }}>
                          <TextField
                            fullWidth
                            size="small"
                            label="Nombre del rol"
                            value={p.etiqueta}
                            onChange={(e) => cambiarParticipante(p.clave, { etiqueta: e.target.value })}
                          />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 5 }}>
                          <TextField
                            fullWidth
                            select
                            size="small"
                            label="Puede ser"
                            value={p.tipoCliente}
                            onChange={(e) =>
                              cambiarParticipante(p.clave, {
                                tipoCliente: e.target.value as TipoParticipante,
                              })
                            }
                          >
                            {(Object.keys(ETIQUETA_TIPO_PARTICIPANTE) as TipoParticipante[]).map((t) => (
                              <MenuItem key={t} value={t}>
                                {ETIQUETA_TIPO_PARTICIPANTE[t]}
                              </MenuItem>
                            ))}
                          </TextField>
                        </Grid>
                      </Grid>
                      <Box sx={{ mt: 1, display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                        {original?.campos.map((c) => (
                          <Chip key={c.clave} size="small" variant="outlined" label={c.etiqueta} />
                        ))}
                      </Box>
                    </Box>
                  )
                })}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6">Variables</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Datos del documento. Su etiqueta y tipo se cambian en{' '}
                <RouterLink to="/variables">Variables</RouterLink>.
              </Typography>
              <List dense>
                {plantilla.variables.map((v) => (
                  <ListItem key={v.id} disableGutters>
                    <ListItemText
                      primary={v.etiqueta}
                      secondary={`{{${v.clave}}} · ${ETIQUETA_TIPO[v.tipo]}`}
                    />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Box sx={{ mt: 3 }}>
        <GuiaMarcas />
      </Box>
    </>
  )
}

function aEdicion(p: PlantillaDetalle): EdicionPlantilla {
  return {
    nombre: p.nombre,
    descripcion: p.descripcion ?? '',
    categoria: p.categoria ?? '',
    activo: p.activo,
    participantes: p.participantes.map(({ clave, etiqueta, tipoCliente }) => ({
      clave,
      etiqueta,
      tipoCliente,
    })),
  }
}
