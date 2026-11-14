import UploadFile from '@mui/icons-material/UploadFile'
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import { listarPlantillas, subirPlantilla } from '../api'
import { GuiaMarcas } from '../components/GuiaMarcas'

export function PlantillasPage() {
  const navegar = useNavigate()
  const [verInactivas, setVerInactivas] = useState(false)
  const [subiendo, setSubiendo] = useState(false)
  const consulta = useQuery({
    queryKey: ['plantillas', !verInactivas],
    queryFn: () => listarPlantillas(!verInactivas),
  })

  return (
    <>
      <EncabezadoPagina
        titulo="Plantillas"
        descripcion="Documentos Word con marcas que el sistema completa."
        acciones={
          <Button variant="contained" startIcon={<UploadFile />} onClick={() => setSubiendo(true)}>
            Subir plantilla
          </Button>
        }
      />
      <GuiaMarcas />
      <FormControlLabel
        control={<Switch checked={verInactivas} onChange={(e) => setVerInactivas(e.target.checked)} />}
        label="Mostrar también las desactivadas"
        sx={{ mb: 1 }}
      />
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Nombre</TableCell>
              <TableCell>Categoría</TableCell>
              <TableCell>Versión</TableCell>
              <TableCell>Estado</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.map((p) => (
              <TableRow
                key={p.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => navegar(`/plantillas/${p.id}`)}
              >
                <TableCell>
                  <Typography>{p.nombre}</Typography>
                  {p.descripcion && (
                    <Typography variant="body2" color="text.secondary">
                      {p.descripcion}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{p.categoria}</TableCell>
                <TableCell>v{p.versionActual}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={p.activo ? 'Activa' : 'Desactivada'}
                    color={p.activo ? 'success' : 'default'}
                    variant="outlined"
                  />
                </TableCell>
              </TableRow>
            ))}
            {consulta.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  Todavía no hay plantillas. Suba la primera con el botón “Subir plantilla”.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <SubirPlantillaDialog
        abierto={subiendo}
        onCerrar={() => setSubiendo(false)}
        onSubida={(id) => navegar(`/plantillas/${id}`)}
      />
    </>
  )
}

function SubirPlantillaDialog({
  abierto,
  onCerrar,
  onSubida,
}: {
  abierto: boolean
  onCerrar: () => void
  onSubida: (id: string) => void
}) {
  const queryClient = useQueryClient()
  const [archivo, setArchivo] = useState<File | null>(null)
  const [nombre, setNombre] = useState('')
  const [categoria, setCategoria] = useState('')
  const [descripcion, setDescripcion] = useState('')

  const subir = useMutation({
    mutationFn: () => subirPlantilla({ archivo: archivo!, nombre, categoria, descripcion }),
    onSuccess: (plantilla) => {
      queryClient.invalidateQueries({ queryKey: ['plantillas'] })
      cerrar()
      onSubida(plantilla.id)
    },
  })

  function cerrar() {
    setArchivo(null)
    setNombre('')
    setCategoria('')
    setDescripcion('')
    subir.reset()
    onCerrar()
  }

  return (
    <Dialog open={abierto} onClose={cerrar} maxWidth="sm" fullWidth>
      <DialogTitle>Subir plantilla</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {subir.error && <Alert severity="error">{subir.error.message}</Alert>}
          <Button variant="outlined" component="label" startIcon={<UploadFile />}>
            {archivo ? archivo.name : 'Elegir archivo Word (.docx)'}
            <input
              hidden
              type="file"
              accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              onChange={(e) => {
                const elegido = e.target.files?.[0] ?? null
                setArchivo(elegido)
                if (elegido && !nombre) setNombre(elegido.name.replace(/\.docx$/i, ''))
              }}
            />
          </Button>
          <TextField label="Nombre" required value={nombre} onChange={(e) => setNombre(e.target.value)} />
          <TextField
            label="Categoría"
            placeholder="Poderes, Declaraciones juradas, Contratos…"
            value={categoria}
            onChange={(e) => setCategoria(e.target.value)}
          />
          <TextField
            label="Descripción"
            multiline
            minRows={2}
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={cerrar}>Cancelar</Button>
        <Button
          variant="contained"
          disabled={!archivo || !nombre.trim()}
          loading={subir.isPending}
          onClick={() => subir.mutate()}
        >
          Subir
        </Button>
      </DialogActions>
    </Dialog>
  )
}
