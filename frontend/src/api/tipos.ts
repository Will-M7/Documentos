/** Respuesta paginada estándar del backend (comun/api/dto/Pagina.java). */
export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}
