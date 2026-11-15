import ArrowBack from '@mui/icons-material/ArrowBack'
import CheckCircle from '@mui/icons-material/CheckCircle'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import type { DatosCliente } from '../../clientes/api'
import { CampoVariable } from '../../documentos/components/CampoVariable'
import { ParticipanteCampos } from '../../documentos/components/ParticipanteCampos'
import { PARTICIPANTE_VACIO } from '../../documentos/participante'
import { hoy } from '../../../lib/fechas'
import { enviarSolicitud, plantillaPublica, type PlantillaPublica } from '../../solicitudes/api'

export function PortalPedirPage() {
  const { id = '' } = useParams()
  const consulta = useQuery({ queryKey: ['portal', 'plantilla', id], queryFn: () => plantillaPublica(id) })
  if (consulta.isPending) return <CircularProgress />
  if (consulta.error) return <Alert severity="error">{consulta.error.message}</Alert>
  return <Formulario plantilla={consulta.data} />
}

function Formulario({ plantilla }: { plantilla: PlantillaPublica }) {
  const [participantes, setParticipantes] = useState<Record<string, DatosCliente>>(() =>
    Object.fromEntries(
      plantilla.participantes.map((p) => [
        p.clave,
        {
          ...PARTICIPANTE_VACIO,
          tipo: p.tipoCliente === 'EMPRESA' ? 'EMPRESA' : 'PERSONA',
          tipoDocumento: p.tipoCliente === 'EMPRESA' ? 'RUC' : 'DNI',
        },
      ]),
    ),
  )
  const [variables, setVariables] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      plantilla.variables.map((v) => [v.clave, v.valorDefecto ?? (v.tipo === 'FECHA' ? hoy() : '')]),
    ),
  )
  const [contacto, setContacto] = useState({ nombre: '', telefono: '', correo: '' })
  const [acepta, setAcepta] = useState(false)

  const enviar = useMutation({
    mutationFn: () =>
      enviarSolicitud({
        plantillaId: plantilla.id,
        variables,
        participantes,
        contactoNombre: contacto.nombre,
        contactoTelefono: contacto.telefono,
        contactoCorreo: contacto.correo,
        aceptaDatos: acepta,
      }),
    onSuccess: () => window.scrollTo({ top: 0 }),
  })

  if (enviar.data) {
    return (
      <Card>
        <CardContent>
          <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <CheckCircle color="success" fontSize="large" />
              <Typography variant="h5">Solicitud enviada</Typography>
            </Stack>
            <Typography>Su código de seguimiento es:</Typography>
            <Typography variant="h3" component="p" sx={{ fontFamily: 'monospace', letterSpacing: 2 }}>
              {enviar.data.codigo}
            </Typography>
            <Typography color="text.secondary">
              Anótelo o tome una captura de pantalla. Con él puede consultar cuándo está listo su documento.
            </Typography>
            <Button
              variant="contained"
              component={RouterLink}
              to={`/portal/estado?codigo=${enviar.data.codigo}`}
            >
              Consultar estado
            </Button>
          </Stack>
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      <Button component={RouterLink} to="/portal" startIcon={<ArrowBack />} sx={{ mb: 2 }}>
        Otros documentos
      </Button>
      <Typography variant="h5" component="h1" gutterBottom>
        {plantilla.nombre}
      </Typography>
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          enviar.mutate()
        }}
      >
        <Stack spacing={2}>
          {plantilla.participantes.map((p) => (
            <ParticipanteCampos
              key={p.clave}
              participante={p}
              conBusqueda={false}
              valor={participantes[p.clave]}
              onCambiar={(datos) => setParticipantes((actual) => ({ ...actual, [p.clave]: datos }))}
            />
          ))}

          {plantilla.variables.length > 0 && (
            <Card variant="outlined">
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Datos del documento
                </Typography>
                <Grid container spacing={2}>
                  {plantilla.variables.map((v) => (
                    <Grid key={v.clave} size={{ xs: 12, sm: v.tipo === 'TEXTO_LARGO' ? 12 : 6 }}>
                      <CampoVariable
                        variable={v}
                        valor={variables[v.clave] ?? ''}
                        onCambiar={(valor) => setVariables((actual) => ({ ...actual, [v.clave]: valor }))}
                      />
                    </Grid>
                  ))}
                </Grid>
              </CardContent>
            </Card>
          )}

          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" gutterBottom>
                ¿Cómo le avisamos?
              </Typography>
              <Grid container spacing={2}>
                <Grid size={12}>
                  <TextField
                    fullWidth
                    size="small"
                    required
                    label="Su nombre"
                    value={contacto.nombre}
                    onChange={(e) => setContacto({ ...contacto, nombre: e.target.value })}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 5 }}>
                  <TextField
                    fullWidth
                    size="small"
                    label="Teléfono o WhatsApp"
                    value={contacto.telefono}
                    onChange={(e) => setContacto({ ...contacto, telefono: e.target.value })}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 7 }}>
                  <TextField
                    fullWidth
                    size="small"
                    type="email"
                    label="Correo"
                    value={contacto.correo}
                    onChange={(e) => setContacto({ ...contacto, correo: e.target.value })}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          <FormControlLabel
            control={<Checkbox checked={acepta} onChange={(e) => setAcepta(e.target.checked)} />}
            label="Autorizo el tratamiento de mis datos personales para preparar este documento, conforme a la Ley N.º 29733 de Protección de Datos Personales."
          />

          {enviar.error && <Alert severity="error">{enviar.error.message}</Alert>}

          <Box>
            <Button
              type="submit"
              variant="contained"
              size="large"
              disabled={!acepta}
              loading={enviar.isPending}
            >
              Enviar solicitud
            </Button>
          </Box>
        </Stack>
      </form>
    </>
  )
}
