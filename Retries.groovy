package es.eci.utils

class Retries {
	
	/**
	 * Retries a Closure a number of times until it
	 * doesn't throw Exception. 
	 * @param times Number of times to retry.
	 * @param miliseconds Number of milliseconds to wait between each iteration.
	 * @param c Closure to retry.
	 * @return Result of the closure
	 */
	static Object retry(int times, int miliseconds, Closure c) {
		def goOn = true;
		Object ret = null;
		for(int i=0; i < times; i++) {						
			try {
				if(goOn == true) {
					ret = c(i);
					goOn = false;
				}
			} catch (Exception e) {
				goOn = true;
				Thread.sleep(miliseconds);
				if(i == (times -1)) {
					throw new Exception(e);
				}
			}
		}
		return ret;
	}
}
