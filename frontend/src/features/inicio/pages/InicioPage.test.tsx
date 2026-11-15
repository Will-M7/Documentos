import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, paginaVacia, respuestaJson, simularApi } from '../../../test/utilidades'

describe('Panel de inicio', () => {
  it('muestra los números del negocio y los gráficos', async () => {
    simularApi({
      'GET /documentos': () => respuestaJson(200, paginaVacia),
      'GET /estadisticas': () =>
        respuestaJson(200, {
          documentosHoy: 3,
          documentosMes: 41,
          documentosMesAnterior: 35,
          clientesTotal: 120,
          clientesNuevosMes: 9,
          consultasMes: 17,
          documentosPorMes: [
            { nombre: '2026-10', cantidad: 35 },
            { nombre: '2026-11', cantidad: 41 },
          ],
          plantillasDelMes: [{ nombre: 'Carta poder simple', cantidad: 20 }],
          usuariosDelMes: [{ nombre: 'Ana', cantidad: 41 }],
        }),
    })
    abrirComoAdmin('/')

    expect(await screen.findByText('+6 respecto al mes anterior (35)')).toBeInTheDocument()
    expect(screen.getByText('Documentos este mes').nextSibling).toHaveTextContent('41')
    expect(screen.getByText('9 nuevos este mes')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'oct 2026: 35, nov 2026: 41' })).toBeInTheDocument()
    expect(screen.getByText('Carta poder simple')).toBeInTheDocument()
  })
})
