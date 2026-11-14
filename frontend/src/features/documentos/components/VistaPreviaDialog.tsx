import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'

/**
 * Muestra el Word dentro de la página tal como quedará, antes de generarlo.
 * Lo dibuja en el navegador (docx-preview), así que no necesita LibreOffice.
 */
export function VistaPreviaDialog({
  docx,
  cargando,
  error,
  generando,
  onCerrar,
  onGenerar,
}: {
  docx: Blob | null
  cargando: boolean
  error: string | null
  generando: boolean
  onCerrar: () => void
  onGenerar: () => void
}) {
  // Ref por estado: el contenido del diálogo se monta después (portal), y así el efecto espera al contenedor
  const [destino, setDestino] = useState<HTMLDivElement | null>(null)
  const [errorDibujo, setErrorDibujo] = useState<string | null>(null)
  const abierto = cargando || !!docx || !!error

  useEffect(() => {
    if (!docx || !destino) return
    let vigente = true
    destino.replaceChildren()
    // Se carga solo al abrir la vista previa: no pesa en la primera carga de la aplicación
    import('docx-preview')
      .then(({ renderAsync }) =>
        renderAsync(docx, destino, undefined, {
          className: 'vista-previa',
          inWrapper: true,
          ignoreLastRenderedPageBreak: true,
        }),
      )
      .then(() => vigente && setErrorDibujo(null))
      .catch(() => vigente && setErrorDibujo('No se pudo mostrar la vista previa. Puede generar igual.'))
    return () => {
      vigente = false
    }
  }, [docx, destino])

  return (
    <Dialog open={abierto} onClose={onCerrar} maxWidth="lg" fullWidth>
      <DialogTitle>Vista previa</DialogTitle>
      <DialogContent dividers sx={{ bgcolor: 'grey.200', p: 0 }}>
        {cargando && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}>
            <CircularProgress />
          </Box>
        )}
        {(error || errorDibujo) && (
          <Alert severity="error" sx={{ m: 2 }}>
            {error ?? errorDibujo}
          </Alert>
        )}
        <Box
          ref={setDestino}
          data-testid="vista-previa"
          sx={{ '& .vista-previa-wrapper': { bgcolor: 'grey.200', py: 2 } }}
        />
      </DialogContent>
      <DialogActions>
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1, px: 1 }}>
          Todavía no se guardó nada: sin número, sin historial.
        </Typography>
        <Button onClick={onCerrar}>Volver a editar</Button>
        <Button variant="contained" disabled={!docx} loading={generando} onClick={onGenerar}>
          Generar documento
        </Button>
      </DialogActions>
    </Dialog>
  )
}
