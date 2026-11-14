import Search from '@mui/icons-material/Search'
import {
  Alert,
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
import { api } from '../../../api/cliente'
import type { Pagina } from '../../../api/tipos'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import { useDebounce } from '../../../lib/useDebounce'

interface Registro {
  id: string
  fecha: string
  usuario: string | null
  accion: string
  descripcion: string
  entidad: string
  detalle: string | null
}

function buscar(q: string, desde: string, hasta: string, pagina: number, tamano: number) {
  const params = new URLSearchParams({ q, pagina: String(pagina), tamano: String(tamano) })
  if (desde) params.set('desde', desde)
  if (hasta) params.set('hasta', hasta)
  return api<Pagina<Registro>>(`/auditoria?${params}`)
}

/** Quién hizo qué y cuándo. Solo lectura. */
export function AuditoriaPage() {
  const [texto, setTexto] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(50)
  const q = useDebounce(texto)
  const consulta = useQuery({
    queryKey: ['auditoria', q, desde, hasta, pagina, tamano],
    queryFn: () => buscar(q, desde, hasta, pagina, tamano),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <EncabezadoPagina
        titulo="Auditoría"
        descripcion="Registro de ingresos, cambios y documentos generados o descargados. No se puede modificar."
      />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <TextField
            fullWidth
            placeholder="Buscar por usuario, acción o detalle (DNI, n.º de documento…)"
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value)
              setPagina(0)
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
            onChange={(e) => setDesde(e.target.value)}
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
            onChange={(e) => setHasta(e.target.value)}
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
              <TableCell>Fecha</TableCell>
              <TableCell>Usuario</TableCell>
              <TableCell>Acción</TableCell>
              <TableCell>Detalle</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.contenido.map((r) => (
              <TableRow key={r.id}>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {new Date(r.fecha).toLocaleString('es-PE')}
                </TableCell>
                <TableCell>{r.usuario ?? '—'}</TableCell>
                <TableCell>
                  <Typography variant="body2">{r.descripcion}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {r.accion}
                  </Typography>
                </TableCell>
                <TableCell>{r.detalle}</TableCell>
              </TableRow>
            ))}
            {consulta.data?.contenido.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  Sin registros con esos filtros.
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
          rowsPerPageOptions={[25, 50, 100]}
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
