import Add from '@mui/icons-material/Add'
import Search from '@mui/icons-material/Search'
import {
  Alert,
  Button,
  Chip,
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
} from '@mui/material'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import { useDebounce } from '../../../lib/useDebounce'
import { buscarClientes, type Cliente } from '../api'
import { ClienteFormDialog } from '../components/ClienteFormDialog'

export function ClientesPage() {
  const [texto, setTexto] = useState('')
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(20)
  const [dialogo, setDialogo] = useState<{ abierto: boolean; cliente: Cliente | null }>({
    abierto: false,
    cliente: null,
  })
  const q = useDebounce(texto)

  const consulta = useQuery({
    queryKey: ['clientes', q, pagina, tamano],
    queryFn: () => buscarClientes(q, pagina, tamano),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <EncabezadoPagina
        titulo="Clientes"
        descripcion="Personas y empresas registradas. Haga clic en una fila para editarla."
        acciones={
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => setDialogo({ abierto: true, cliente: null })}
          >
            Nuevo cliente
          </Button>
        }
      />

      <TextField
        fullWidth
        placeholder="Buscar por número de documento o nombre"
        value={texto}
        onChange={(e) => {
          setTexto(e.target.value)
          setPagina(0)
        }}
        sx={{ mb: 2, bgcolor: 'background.paper' }}
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

      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Documento</TableCell>
              <TableCell>Nombre o razón social</TableCell>
              <TableCell>Dirección</TableCell>
              <TableCell>Teléfono</TableCell>
              <TableCell>Origen</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.contenido.map((cliente) => (
              <TableRow
                key={cliente.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => setDialogo({ abierto: true, cliente })}
              >
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {cliente.tipoDocumento} {cliente.numeroDocumento}
                </TableCell>
                <TableCell>{cliente.nombreCompleto}</TableCell>
                <TableCell>{cliente.direccion}</TableCell>
                <TableCell>{cliente.telefono}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={cliente.origen === 'MANUAL' ? 'Manual' : cliente.origen}
                    variant="outlined"
                  />
                </TableCell>
              </TableRow>
            ))}
            {consulta.data?.contenido.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  {q ? 'No se encontraron clientes con ese texto.' : 'Todavía no hay clientes registrados.'}
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

      <ClienteFormDialog
        abierto={dialogo.abierto}
        cliente={dialogo.cliente}
        onCerrar={() => setDialogo({ abierto: false, cliente: null })}
      />
    </>
  )
}
