import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, paginaVacia, respuestaJson, simularApi } from '../test/utilidades'

describe('Renovación de la sesión', () => {
  it('si el acceso venció, lo renueva una vez y repite la petición sin que el usuario lo note', async () => {
    const tokensUsados: (string | null)[] = []
    simularApi({
      'GET /clientes': (_, init) => {
        const token = new Headers(init?.headers).get('Authorization')
        tokensUsados.push(token)
        return token === 'Bearer acceso-nuevo'
          ? respuestaJson(200, paginaVacia)
          : respuestaJson(401, { status: 401, detail: 'Token vencido' })
      },
      'POST /auth/renovar': () =>
        respuestaJson(200, {
          token: 'acceso-nuevo',
          expiraEn: new Date(Date.now() + 900_000).toISOString(),
          tokenSesion: 'sesion-nueva',
          sesionExpiraEn: new Date(Date.now() + 3_600_000).toISOString(),
          usuario: { id: '1', usuario: 'admin', nombre: 'Ana Pérez', rol: 'ADMIN' },
        }),
    })
    abrirComoAdmin('/clientes')

    expect(await screen.findByText('Todavía no hay clientes registrados.')).toBeInTheDocument()
    expect(tokensUsados).toEqual(['Bearer token-de-prueba', 'Bearer acceso-nuevo'])
    expect(JSON.parse(localStorage.getItem('estudio-documental.sesion')!).tokenSesion).toBe('sesion-nueva')
  })

  it('si la sesión ya no se puede renovar, vuelve al ingreso', async () => {
    simularApi({
      'GET /clientes': () => respuestaJson(401, { status: 401 }),
      'POST /auth/renovar': () => respuestaJson(401, { status: 401, detail: 'La sesión venció.' }),
      'POST /auth/salir': () => respuestaJson(204, null),
    })
    abrirComoAdmin('/clientes')

    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  })
})
