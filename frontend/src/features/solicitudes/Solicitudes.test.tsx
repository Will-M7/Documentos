import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { abrirComoAdmin, paginaVacia, respuestaJson, simularApi } from '../../test/utilidades'

const plantilla = {
  id: 'p1',
  nombre: 'Carta poder simple',
  descripcion: 'Para que otra persona haga un trámite por usted.',
  categoria: 'Poderes',
  participantes: [
    {
      clave: 'otorgante',
      etiqueta: 'Poderdante',
      tipoCliente: 'PERSONA',
      campos: [{ clave: 'nombre_completo', etiqueta: 'Nombre completo' }],
    },
  ],
  variables: [
    { id: 'v1', clave: 'lugar', etiqueta: 'Lugar', tipo: 'TEXTO', valorDefecto: null, descripcion: null },
  ],
}

const resumen = {
  id: 's1',
  codigo: 'S-AB12CD',
  plantillaId: 'p1',
  plantillaNombre: 'Carta poder simple',
  estado: 'PENDIENTE',
  contactoNombre: 'Luis Rojas',
  contactoTelefono: '987654321',
  contactoCorreo: null,
  motivoRechazo: null,
  documentoId: null,
  creadoEn: '2026-11-14T10:00:00Z',
  atendidaEn: null,
}

function abrirSinSesion(ruta: string) {
  window.history.pushState({}, '', ruta)
  return render(<App />)
}

describe('Portal de clientes', () => {
  it('el cliente elige un documento, envía sus datos y recibe su código', async () => {
    let envio: Record<string, unknown> | undefined
    simularApi({
      'GET /publico/plantillas': () => respuestaJson(200, [plantilla]),
      'GET /publico/plantillas/p1': () => respuestaJson(200, plantilla),
      'POST /publico/solicitudes': (_, init) => {
        envio = JSON.parse(String(init?.body))
        return respuestaJson(201, { codigo: 'S-AB12CD', mensaje: 'Recibida' })
      },
    })
    const usuario = userEvent.setup()
    abrirSinSesion('/portal')

    await usuario.click(await screen.findByText('Carta poder simple'))
    expect(screen.queryByRole('button', { name: /buscar/i })).not.toBeInTheDocument()
    await usuario.type(await screen.findByLabelText(/^Nombres/), 'Luis')
    await usuario.type(screen.getByLabelText(/^Lugar/), 'Lima')
    await usuario.type(screen.getByLabelText(/su nombre/i), 'Luis Rojas')

    const enviar = screen.getByRole('button', { name: 'Enviar solicitud' })
    expect(enviar).toBeDisabled()
    await usuario.click(screen.getByRole('checkbox'))
    await usuario.click(enviar)

    expect(await screen.findByText('S-AB12CD')).toBeInTheDocument()
    expect(envio).toMatchObject({
      plantillaId: 'p1',
      variables: { lugar: 'Lima' },
      contactoNombre: 'Luis Rojas',
      contactoCorreo: null,
      aceptaDatos: true,
    })
    expect((envio!.participantes as Record<string, { nombres: string }>).otorgante.nombres).toBe('Luis')
  })

  it('consulta el estado con el código', async () => {
    simularApi({
      'GET /publico/solicitudes/S-AB12CD': () =>
        respuestaJson(200, {
          codigo: 'S-AB12CD',
          documento: 'Carta poder simple',
          estado: 'ATENDIDA',
          mensaje: 'Su documento está listo.',
          enviadaEn: '2026-11-14T10:00:00Z',
        }),
    })
    abrirSinSesion('/portal/estado?codigo=S-AB12CD')
    expect(await screen.findByText('Su documento está listo.')).toBeInTheDocument()
    expect(screen.getByText('Atendida')).toBeInTheDocument()
  })
})

describe('Bandeja de solicitudes', () => {
  it('muestra el contador en el menú y las pendientes', async () => {
    simularApi({
      'GET /estadisticas': () => respuestaJson(500, { status: 500, detail: 'sin datos' }),
      'GET /solicitudes/pendientes': () => respuestaJson(200, { pendientes: 3 }),
      'GET /solicitudes': () =>
        respuestaJson(200, { ...paginaVacia, contenido: [resumen], totalElementos: 1, totalPaginas: 1 }),
    })
    abrirComoAdmin('/solicitudes')
    expect(await screen.findByText('S-AB12CD')).toBeInTheDocument()
    expect(screen.getByText('Luis Rojas')).toBeInTheDocument()
    expect(await screen.findByLabelText('3 pendientes')).toBeInTheDocument()
  })

  it('atender rellena el formulario con lo enviado y genera con la solicitud', async () => {
    let pedido: Record<string, unknown> | undefined
    simularApi({
      'GET /solicitudes/s1': () =>
        respuestaJson(200, {
          resumen,
          datos: {
            variables: { lugar: 'Cusco' },
            participantes: {
              otorgante: {
                tipo: 'PERSONA',
                tipoDocumento: 'DNI',
                numeroDocumento: '41234567',
                nombres: 'Luis',
                apellidoPaterno: 'Rojas',
                apellidoMaterno: 'Paz',
                direccion: 'Jr. Lima 1',
              },
            },
          },
        }),
      'GET /plantillas/p1': () =>
        respuestaJson(200, { ...plantilla, versionActual: 1, activo: true, versiones: [] }),
      'POST /documentos': (_, init) => {
        pedido = JSON.parse(String(init?.body))
        return respuestaJson(201, {
          resumen: {
            id: 'd1',
            numero: 8,
            titulo: 'Carta poder simple - Luis Rojas Paz',
            plantillaId: 'p1',
            plantillaNombre: 'Carta poder simple',
            usuarioNombre: 'Ana Pérez',
            creadoEn: '2026-11-14T10:00:00Z',
            tienePdf: false,
            participantes: [],
          },
          variables: {},
        })
      },
    })
    const usuario = userEvent.setup()
    abrirComoAdmin('/documentos/nuevo?solicitud=s1')

    expect(await screen.findByText('S-AB12CD')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Nombres/)).toHaveValue('Luis')
    expect(screen.getByLabelText(/^Lugar/)).toHaveValue('Cusco')

    await usuario.click(screen.getByRole('button', { name: /generar/i }))
    await screen.findByText(/Carta poder simple - Luis Rojas Paz/)
    expect(pedido).toMatchObject({ plantillaId: 'p1', solicitudId: 's1', variables: { lugar: 'Cusco' } })
  })
})
