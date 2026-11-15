import ContentCopy from '@mui/icons-material/ContentCopy'
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import {
  ETIQUETA_ESTADO,
  listarSolicitudes,
  rechazarSolicitud,
  type EstadoSolicitud,
  type SolicitudResumen,
} from '../api'

const COLOR = { PENDIENTE: 'warning', ATENDIDA: 'success', RECHAZADA: 'default' } as const

/** Bandeja de solicitudes que llegan del portal de clientes. */
export function SolicitudesPage() {
  const navegar = useNavigate()
  const [estado, setEstado] = useState<EstadoSolicitud | ''>('PENDIENTE')
  const [pagina, setPagina] = useState(0)
  const [rechazando, setRechazando] = useState<SolicitudResumen | null>(null)
  const [copiado, setCopiado] = useState(false)
  const enlace = `${window.location.origin}/portal`

  const consulta = useQuery({
    queryKey: ['solicitudes', estado, pagina],
    queryFn: () => listarSolicitudes(estado, pagina, 20),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <EncabezadoPagina
        titulo="Solicitudes"
        descripcion="Pedidos que hacen los clientes desde el portal. Revise los datos y genere el documento."
      />
      <Alert
        severity="info"
        sx={{ mb: 2 }}
        action={
          <Button
            color="inherit"
            size="small"
            startIcon={<ContentCopy />}
            onClick={() => navigator.clipboard?.writeText(enlace).then(() => setCopiado(true))}
          >
            {copiado ? 'Copiado' : 'Copiar enlace'}
          </Button>
        }
      >
        Enlace del portal para compartir con los clientes: <strong>{enlace}</strong>. Solo aparecen las
        plantillas marcadas “En el portal”.
      </Alert>

      <Tabs
        value={estado}
        onChange={(_, valor) => {
          setEstado(valor)
          setPagina(0)
        }}
        sx={{ mb: 1 }}
      >
        <Tab value="PENDIENTE" label="Pendientes" />
        <Tab value="ATENDIDA" label="Atendidas" />
        <Tab value="RECHAZADA" label="Rechazadas" />
        <Tab value="" label="Todas" />
      </Tabs>

      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Código</TableCell>
              <TableCell>Recibida</TableCell>
              <TableCell>Documento</TableCell>
              <TableCell>Contacto</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell align="right">Acciones</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.contenido.map((s) => (
              <TableRow key={s.id} hover>
                <TableCell sx={{ fontFamily: 'monospace', whiteSpace: 'nowrap' }}>{s.codigo}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {new Date(s.creadoEn).toLocaleString('es-PE')}
                </TableCell>
                <TableCell>{s.plantillaNombre}</TableCell>
                <TableCell>
                  <Typography variant="body2">{s.contactoNombre}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {[s.contactoTelefono, s.contactoCorreo].filter(Boolean).join(' · ')}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip size="small" color={COLOR[s.estado]} label={ETIQUETA_ESTADO[s.estado]} />
                  {s.motivoRechazo && (
                    <Typography variant="body2" color="text.secondary">
                      {s.motivoRechazo}
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  {s.estado === 'PENDIENTE' && (
                    <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                      <Button size="small" color="error" onClick={() => setRechazando(s)}>
                        Rechazar
                      </Button>
                      <Button
                        size="small"
                        variant="contained"
                        onClick={() => navegar(`/documentos/nuevo?solicitud=${s.id}`)}
                      >
                        Atender
                      </Button>
                    </Box>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {consulta.data?.contenido.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  No hay solicitudes {estado ? ETIQUETA_ESTADO[estado].toLowerCase() + 's' : ''}.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={consulta.data?.totalElementos ?? 0}
          page={pagina}
          rowsPerPage={20}
          rowsPerPageOptions={[20]}
          onPageChange={(_, nueva) => setPagina(nueva)}
        />
      </TableContainer>

      <RechazoDialog solicitud={rechazando} onCerrar={() => setRechazando(null)} />
    </>
  )
}

function RechazoDialog({
  solicitud,
  onCerrar,
}: {
  solicitud: SolicitudResumen | null
  onCerrar: () => void
}) {
  const queryClient = useQueryClient()
  const [motivo, setMotivo] = useState('')
  const rechazar = useMutation({
    mutationFn: () => rechazarSolicitud(solicitud!.id, motivo),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['solicitudes'] })
      queryClient.invalidateQueries({ queryKey: ['solicitudes-pendientes'] })
      setMotivo('')
      onCerrar()
    },
  })
  return (
    <Dialog open={!!solicitud} onClose={onCerrar} maxWidth="sm" fullWidth>
      <DialogTitle>Rechazar {solicitud?.codigo}</DialogTitle>
      <DialogContent>
        {rechazar.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {rechazar.error.message}
          </Alert>
        )}
        <TextField
          fullWidth
          multiline
          minRows={2}
          label="Motivo"
          helperText="El cliente lo verá al consultar su código."
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          sx={{ mt: 1 }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar}>Cancelar</Button>
        <Button
          color="error"
          variant="contained"
          disabled={!motivo.trim()}
          loading={rechazar.isPending}
          onClick={() => rechazar.mutate()}
        >
          Rechazar
        </Button>
      </DialogActions>
    </Dialog>
  )
}
