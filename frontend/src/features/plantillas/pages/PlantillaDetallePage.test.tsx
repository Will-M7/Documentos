import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, respuestaJson, simularApi } from '../../../test/utilidades'

const detalle = {
  id: 'p1',
  nombre: 'Carta poder simple',
  descripcion: null,
  categoria: 'Poderes',
  versionActual: 1,
  activo: true,
  participantes: [
    {
      clave: 'otorgante',
      etiqueta: 'Otorgante',
      tipoCliente: 'CUALQUIERA',
      campos: [{ clave: 'nombres', etiqueta: 'Nombres' }],
    },
  ],
  variables: [
    {
      id: 'v1',
      clave: 'fecha',
      etiqueta: 'Fecha del documento',
      tipo: 'FECHA',
      valorDefecto: null,
      descripcion: null,
    },
  ],
  versiones: [
    { numero: 1, creadoEn: '2026-11-14T10:00:00Z', marcas: ['{{fecha}}', '{{otorgante.nombres}}'] },
  ],
}

describe('Detalle de plantilla', () => {
  it('muestra participantes, variables y versiones, y guarda el nombre del rol', async () => {
    let enviado: { participantes: { etiqueta: string; tipoCliente: string }[] } | undefined
    simularApi({
      'GET /plantillas/p1': () => respuestaJson(200, detalle),
      'PUT /plantillas/p1': (_, init) => {
        enviado = JSON.parse(String(init?.body))
        return respuestaJson(200, detalle)
      },
    })
    abrirComoAdmin('/plantillas/p1')

    const rol = await screen.findByLabelText('Nombre del rol')
    expect(screen.getByText('Fecha del documento')).toBeInTheDocument()
    expect(screen.getByText('Versión 1 (actual)')).toBeInTheDocument()

    await userEvent.clear(rol)
    await userEvent.type(rol, 'Poderdante')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument()
    expect(enviado?.participantes[0]).toEqual({
      clave: 'otorgante',
      etiqueta: 'Poderdante',
      tipoCliente: 'CUALQUIERA',
    })
  })
})
