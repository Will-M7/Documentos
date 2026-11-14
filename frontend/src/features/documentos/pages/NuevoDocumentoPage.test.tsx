import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, paginaVacia, respuestaJson, simularApi } from '../../../test/utilidades'

const plantilla = {
  id: 'p1',
  nombre: 'Carta poder simple',
  descripcion: null,
  categoria: 'Poderes',
  versionActual: 1,
  activo: true,
  participantes: [
    {
      clave: 'otorgante',
      etiqueta: 'Poderdante',
      tipoCliente: 'PERSONA',
      campos: [
        { clave: 'nombre_completo', etiqueta: 'Nombre completo' },
        { clave: 'direccion', etiqueta: 'Dirección' },
      ],
    },
  ],
  variables: [
    { id: 'v1', clave: 'lugar', etiqueta: 'Lugar', tipo: 'TEXTO', valorDefecto: null, descripcion: null },
  ],
  versiones: [],
}

const ana = {
  id: 'c1',
  tipo: 'PERSONA',
  tipoDocumento: 'DNI',
  numeroDocumento: '41234567',
  nombres: 'Ana María',
  apellidoPaterno: 'Quispe',
  apellidoMaterno: 'Rojas',
  razonSocial: null,
  direccion: 'Av. Sol 123',
  telefono: null,
  correo: null,
  nombreCompleto: 'Ana María Quispe Rojas',
  origen: 'MANUAL',
  actualizadoEn: '2026-11-14T10:00:00Z',
}

describe('Nuevo documento', () => {
  it('elige plantilla, completa con un cliente registrado y genera', async () => {
    let pedido: Record<string, unknown> | undefined
    simularApi({
      'GET /plantillas': () => respuestaJson(200, [{ ...plantilla, actualizadoEn: '2026-11-14T10:00:00Z' }]),
      'GET /plantillas/p1': () => respuestaJson(200, plantilla),
      'POST /consultas/DNI/41234567': () =>
        respuestaJson(200, {
          encontrado: true,
          origen: 'CLIENTES',
          cliente: ana,
          mensaje: 'Datos cargados desde Clientes (sin costo).',
        }),
      'GET /documentos': () => respuestaJson(200, paginaVacia),
      'POST /documentos': (_, init) => {
        pedido = JSON.parse(String(init?.body))
        return respuestaJson(201, {
          resumen: {
            id: 'd1',
            numero: 7,
            titulo: 'Carta poder simple - Ana María Quispe Rojas',
            plantillaId: 'p1',
            plantillaNombre: 'Carta poder simple',
            usuarioNombre: 'Ana Pérez',
            creadoEn: '2026-11-14T10:00:00Z',
            tienePdf: false,
            participantes: [],
          },
          variables: {},
          marcas: {},
          avisoPdf: 'PDF no disponible: instale LibreOffice en el servidor para generar PDF automáticamente.',
        })
      },
    })
    abrirComoAdmin('/documentos/nuevo')

    await userEvent.click(await screen.findByText('Carta poder simple'))
    await userEvent.type(await screen.findByLabelText(/número/i), '41234567')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(await screen.findByText(/Datos cargados desde Clientes/)).toBeInTheDocument()
    expect(screen.getByLabelText(/^nombres/i)).toHaveValue('Ana María')

    await userEvent.type(screen.getByLabelText('Lugar'), 'Cusco')
    await userEvent.click(screen.getByRole('button', { name: 'Generar documento' }))

    expect(await screen.findByText('Documento n.º 7 generado')).toBeInTheDocument()
    expect(screen.getByText(/instale LibreOffice/)).toBeInTheDocument()
    expect(pedido).toMatchObject({
      plantillaId: 'p1',
      variables: { lugar: 'Cusco' },
      participantes: {
        otorgante: { numeroDocumento: '41234567', nombres: 'Ana María', direccion: 'Av. Sol 123' },
      },
    })
  })
})
