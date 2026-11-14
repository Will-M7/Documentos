import Replay from '@mui/icons-material/Replay'
import Search from '@mui/icons-material/Search'
import {
  Alert,
  Box,
  Button,
  Grid,
  InputAdornment,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import { useDebounce } from '../../../lib/useDebounce'
import { buscarDocumentos } from '../api'
import { AccionesDocumento } from '../components/AccionesDocumento'

export function HistorialPage() {
  const [texto, setTexto] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(20)
  const q = useDebounce(texto)

  const consulta = useQuery({
    queryKey: ['documentos', q, desde, hasta, pagina, tamano],
    queryFn: () => buscarDocumentos({ q, desde, hasta, pagina, tamano }),
    placeholderData: keepPreviousData,
  })
  const reiniciarPagina = () => setPagina(0)

  return (
    <>
      <EncabezadoPagina
        titulo="Historial"
        descripcion="Todos los documentos generados, del más reciente al más antiguo."
      />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <TextField
            fullWidth
            placeholder="Buscar por título, plantilla, nombre o documento de identidad"
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value)
              reiniciarPagina()
            }}
            sx={{ bgcolor: 'background.paper' }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
              },
            }}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            fullWidth
            type="date"
            label="Desde"
            value={desde}
            onChange={(e) => {
              setDesde(e.target.value)
              reiniciarPagina()
            }}
            sx={{ bgcolor: 'background.paper' }}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <TextField
            fullWidth
            type="date"
            label="Hasta"
            value={hasta}
            onChange={(e) => {
              setHasta(e.target.value)
              reiniciarPagina()
            }}
            sx={{ bgcolor: 'background.paper' }}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Grid>
      </Grid>

      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>N.º</TableCell>
              <TableCell>Fecha</TableCell>
              <TableCell>Documento</TableCell>
              <TableCell>Participantes</TableCell>
              <TableCell>Generó</TableCell>
              <TableCell align="right">Acciones</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.contenido.map((d) => (
              <TableRow key={d.id} hover>
                <TableCell>{d.numero}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {new Date(d.creadoEn).toLocaleString('es-PE')}
                </TableCell>
                <TableCell>
                  <Typography>{d.titulo}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {d.plantillaNombre}
                  </Typography>
                </TableCell>
                <TableCell>
                  {d.participantes.map((p) => (
                    <Typography key={p.participante} variant="body2">
                      {p.etiqueta}: {p.nombre} ({p.documentoIdentidad})
                    </Typography>
                  ))}
                </TableCell>
                <TableCell>{d.usuarioNombre}</TableCell>
                <TableCell align="right">
                  <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                    <AccionesDocumento documento={d} compacto />
                    <Button
                      size="small"
                      startIcon={<Replay />}
                      component={RouterLink}
                      to={`/documentos/nuevo?desde=${d.id}`}
                    >
                      Usar de nuevo
                    </Button>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
            {consulta.data?.contenido.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  No hay documentos con esos filtros.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={consulta.data?.totalElementos ?? 0}
          page={pagina}
          rowsPerPage={tamano}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, nueva) => setPagina(nueva)}
          onRowsPerPageChange={(e) => {
            setTamano(Number(e.target.value))
            setPagina(0)
          }}
        />
      </TableContainer>
    </>
  )
}
