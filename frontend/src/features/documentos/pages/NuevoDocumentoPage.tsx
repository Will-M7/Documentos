import ArrowBack from '@mui/icons-material/ArrowBack'
import CheckCircle from '@mui/icons-material/CheckCircle'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  CircularProgress,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import type { DatosCliente } from '../../clientes/api'
import {
  listarPlantillas,
  obtenerPlantilla,
  type PlantillaDetalle,
  type PlantillaResumen,
} from '../../plantillas/api'
import { generarDocumento, obtenerCliente, obtenerDocumento, type DocumentoDetalle } from '../api'
import { AccionesDocumento } from '../components/AccionesDocumento'
import { CampoVariable } from '../components/CampoVariable'
import { ParticipanteCampos } from '../components/ParticipanteCampos'
import { PARTICIPANTE_VACIO, sinNulos } from '../participante'

/**
 * Flujo principal: elegir plantilla → completar datos → generar.
 * `?plantilla=<id>` abre directo el formulario; `?desde=<documento>` lo rellena con un documento anterior.
 */
export function NuevoDocumentoPage() {
  const [parametros, setParametros] = useSearchParams()
  const plantillaId = parametros.get('plantilla')
  const desde = parametros.get('desde')

  if (!plantillaId && !desde) {
    return <ElegirPlantilla onElegir={(id) => setParametros({ plantilla: id })} />
  }
  return <CargarFormulario key={`${plantillaId}-${desde}`} plantillaId={plantillaId} desde={desde} />
}

function ElegirPlantilla({ onElegir }: { onElegir: (id: string) => void }) {
  const [filtro, setFiltro] = useState('')
  const consulta = useQuery({ queryKey: ['plantillas', true], queryFn: () => listarPlantillas(true) })
  const visibles = (consulta.data ?? []).filter((p) =>
    `${p.nombre} ${p.categoria ?? ''}`.toLowerCase().includes(filtro.toLowerCase()),
  )
  const porCategoria = agrupar(visibles)

  return (
    <>
      <EncabezadoPagina titulo="Nuevo documento" descripcion="Elija la plantilla del documento a preparar." />
      <TextField
        fullWidth
        placeholder="Buscar plantilla"
        value={filtro}
        onChange={(e) => setFiltro(e.target.value)}
        sx={{ mb: 3, bgcolor: 'background.paper' }}
      />
      {consulta.isPending && <CircularProgress />}
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      {consulta.data?.length === 0 && (
        <Alert severity="info">
          Todavía no hay plantillas activas. Un administrador debe subirlas en Administración → Plantillas.
        </Alert>
      )}
      {Object.entries(porCategoria).map(([categoria, plantillas]) => (
        <Box key={categoria} sx={{ mb: 3 }}>
          <Typography variant="overline" color="text.secondary">
            {categoria}
          </Typography>
          <Grid container spacing={2}>
            {plantillas.map((p) => (
              <Grid key={p.id} size={{ xs: 12, sm: 6, lg: 4 }}>
                <Card sx={{ height: '100%' }}>
                  <CardActionArea sx={{ height: '100%' }} onClick={() => onElegir(p.id)}>
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
        </Box>
      ))}
    </>
  )
}

interface ValoresIniciales {
  plantilla: PlantillaDetalle
  variables: Record<string, string>
  participantes: Record<string, DatosCliente>
}

/** Carga la plantilla (y, si se pide, el documento anterior y sus clientes) antes de mostrar el formulario. */
function CargarFormulario({ plantillaId, desde }: { plantillaId: string | null; desde: string | null }) {
  const consulta = useQuery({
    queryKey: ['formulario-documento', plantillaId, desde],
    queryFn: async (): Promise<ValoresIniciales> => {
      const anterior = desde ? await obtenerDocumento(desde) : null
      const plantilla = await obtenerPlantilla(plantillaId ?? anterior!.resumen.plantillaId)
      const participantes: Record<string, DatosCliente> = {}
      for (const p of plantilla.participantes) {
        const previo = anterior?.resumen.participantes.find((x) => x.participante === p.clave)
        const cliente = previo ? await obtenerCliente(previo.clienteId).catch(() => null) : null
        participantes[p.clave] = cliente
          ? ({ ...PARTICIPANTE_VACIO, ...sinNulos(cliente) } as DatosCliente)
          : {
              ...PARTICIPANTE_VACIO,
              tipo: p.tipoCliente === 'EMPRESA' ? 'EMPRESA' : 'PERSONA',
              tipoDocumento: p.tipoCliente === 'EMPRESA' ? 'RUC' : 'DNI',
            }
      }
      const variables: Record<string, string> = {}
      for (const v of plantilla.variables) {
        variables[v.clave] =
          anterior?.variables[v.clave] ?? v.valorDefecto ?? (v.tipo === 'FECHA' ? hoy() : '')
      }
      return { plantilla, variables, participantes }
    },
    staleTime: Infinity,
  })

  if (consulta.isPending) return <CircularProgress />
  if (consulta.error) return <Alert severity="error">{consulta.error.message}</Alert>
  return <Formulario iniciales={consulta.data} />
}

function Formulario({ iniciales }: { iniciales: ValoresIniciales }) {
  const navegar = useNavigate()
  const { plantilla } = iniciales
  const [titulo, setTitulo] = useState('')
  const [variables, setVariables] = useState(iniciales.variables)
  const [participantes, setParticipantes] = useState(iniciales.participantes)
  const [resultado, setResultado] = useState<DocumentoDetalle | null>(null)

  const generar = useMutation({
    mutationFn: () => generarDocumento({ plantillaId: plantilla.id, titulo, variables, participantes }),
    onSuccess: (documento) => {
      setResultado(documento)
      window.scrollTo({ top: 0 })
    },
  })

  if (resultado) {
    return (
      <Resultado
        documento={resultado}
        onOtro={() => navegar(`/documentos/nuevo?plantilla=${plantilla.id}`, { replace: true })}
      />
    )
  }

  return (
    <>
      <Button component={RouterLink} to="/documentos/nuevo" startIcon={<ArrowBack />} sx={{ mb: 2 }}>
        Elegir otra plantilla
      </Button>
      <EncabezadoPagina
        titulo={plantilla.nombre}
        descripcion="Busque a cada persona por su documento: si ya está registrada, sus datos se completan solos."
      />
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          generar.mutate()
        }}
      >
        <Stack spacing={2}>
          {plantilla.participantes.map((p) => (
            <ParticipanteCampos
              key={p.clave}
              participante={p}
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

          <TextField
            label="Título para el historial (opcional)"
            placeholder={`${plantilla.nombre} - nombre del primer participante`}
            value={titulo}
            onChange={(e) => setTitulo(e.target.value)}
            sx={{ bgcolor: 'background.paper' }}
          />

          {generar.error && <Alert severity="error">{generar.error.message}</Alert>}

          <Box>
            <Button type="submit" variant="contained" size="large" loading={generar.isPending}>
              Generar documento
            </Button>
          </Box>
        </Stack>
      </form>
    </>
  )
}

function Resultado({ documento, onOtro }: { documento: DocumentoDetalle; onOtro: () => void }) {
  const { resumen } = documento
  return (
    <Card>
      <CardContent>
        <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <CheckCircle color="success" fontSize="large" />
            <Box>
              <Typography variant="h5">Documento n.º {resumen.numero} generado</Typography>
              <Typography color="text.secondary">{resumen.titulo}</Typography>
            </Box>
          </Stack>
          <AccionesDocumento documento={resumen} />
          {documento.avisoPdf && <Alert severity="warning">{documento.avisoPdf}</Alert>}
          <Typography variant="body2" color="text.secondary">
            Los datos de {resumen.participantes.map((p) => p.nombre).join(', ') || 'los participantes'}{' '}
            quedaron guardados en Clientes.
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button variant="contained" onClick={onOtro}>
              Otro documento con esta plantilla
            </Button>
            <Button component={RouterLink} to="/historial">
              Ir al historial
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  )
}

function agrupar(plantillas: PlantillaResumen[]) {
  return plantillas.reduce<Record<string, PlantillaResumen[]>>((grupos, p) => {
    const clave = p.categoria ?? 'Sin categoría'
    ;(grupos[clave] ??= []).push(p)
    return grupos
  }, {})
}

function hoy() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
