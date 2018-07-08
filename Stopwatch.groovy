package es.eci.utils

/**
 * Cronómetro sencillo en groovy
 */
class Stopwatch {
	/** Ejecuta la closure, cronometrándola. */
	static long watch(Closure c) {
		Date inicio = new Date()
		c()
		Date fin = new Date()
		return fin.getTime() - inicio.getTime()
	}
}