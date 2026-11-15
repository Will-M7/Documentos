import { AppBar, Box, Button, Container, Toolbar, Typography } from '@mui/material'
import { Link as RouterLink, Outlet } from 'react-router'

/** Marco del portal público: sin menú de la oficina ni datos de usuarios. */
export function PortalLayout() {
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static" elevation={0}>
        <Toolbar sx={{ gap: 1, flexWrap: 'wrap' }}>
          <Typography
            variant="h6"
            component={RouterLink}
            to="/portal"
            sx={{ flexGrow: 1, color: 'inherit', textDecoration: 'none' }}
          >
            Estudio Documental · Trámites en línea
          </Typography>
          <Button color="inherit" component={RouterLink} to="/portal/estado">
            Consultar mi solicitud
          </Button>
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
