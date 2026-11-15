import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemText,
  Typography,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { importarV1 } from './api'

/** Tarjeta de Configuración para traer clientes y plantillas de la versión de escritorio. */
export function ImportarV1() {
  const queryClient = useQueryClient()
  const [archivo, setArchivo] = useState<File | null>(null)
  const importar = useMutation({
    mutationFn: () => importarV1(archivo!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clientes'] })
      queryClient.invalidateQueries({ queryKey: ['plantillas'] })
      queryClient.invalidateQueries({ queryKey: ['variables'] })
    },
  })
  const r = importar.data

  return (
    <Card sx={{ mt: 2 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Traer datos de la versión anterior
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          En la aplicación de escritorio, vaya a Configuración y use <strong>Exportar Clientes</strong> y/o{' '}
          <strong>Exportar Plantillas</strong> (alcance “Todas”). Suba aquí el ZIP. Se puede repetir sin
          duplicar: los clientes que ya existen solo se completan y las plantillas con el mismo nombre se
          saltan.
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Button component="label" variant="outlined" startIcon={<UploadFileOutlined />}>
            Elegir ZIP
            <input
              hidden
              type="file"
              accept=".zip,application/zip"
              aria-label="Archivo ZIP de la versión anterior"
              onChange={(e) => {
                setArchivo(e.target.files?.[0] ?? null)
                importar.reset()
              }}
            />
          </Button>
          <Typography variant="body2">{archivo ? archivo.name : 'Ningún archivo elegido'}</Typography>
          <Button
            variant="contained"
            disabled={!archivo}
            loading={importar.isPending}
            onClick={() => importar.mutate()}
          >
            Importar
          </Button>
        </Box>

        {importar.error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {importar.error.message}
          </Alert>
        )}
        {r && (
          <Alert severity={r.avisos.length ? 'warning' : 'success'} sx={{ mt: 2 }}>
            <strong>Importación terminada.</strong> Clientes: {r.clientesNuevos} nuevos,{' '}
            {r.clientesCompletados} ya existían (se completaron), {r.clientesOmitidos} con errores.
            Plantillas: {r.plantillasCreadas} creadas, {r.plantillasOmitidas} no se importaron.
            {r.plantillasCreadas > 0 && (
              <>
                {' '}
                Revise cada plantilla nueva en Plantillas: los participantes y formatos de la v1 no se traen.
              </>
            )}
            {r.avisos.length > 0 && (
              <List dense disablePadding sx={{ mt: 1 }}>
                {r.avisos.map((aviso) => (
                  <ListItem key={aviso} disableGutters>
                    <ListItemText primary={aviso} />
                  </ListItem>
                ))}
              </List>
            )}
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}
