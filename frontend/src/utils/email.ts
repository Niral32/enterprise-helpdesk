/** Match auth-service `normalizeEmail` (trim + lowercase). */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}
