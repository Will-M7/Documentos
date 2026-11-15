/** Fecha de hoy en formato de campo de fecha (AAAA-MM-DD), según la hora de esta PC. */
export function hoy() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
