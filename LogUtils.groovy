package es.eci.utils

class LogUtils {

	def Closure logger = null
	def nivelLog = 1
	def release = null
	def stream = null
	
	def setNivelLog(def nivel) {
		nivelLog = nivel 
	}	
	
	/**
	 * Constructor con una referencia a la closure apropiada del
	 * script invocante. La Closure
	 * debe contener una llamada al println o al método que se
	 * desee del script invocado por jenkins.
	 * P. ej.: 
	 * // dentro de un script .groovy invocado directamente por jenkins
	 * def LogUtils log = new LogUtils();
	 * log.initLogger({println it});
	 */
	def LogUtils( Closure _logger ) {
		this.initLogger(_logger); 
	}
	
	/**
	 * Imprescindible llamar a initLogger antes de poder usar
	 * cualquiera de los métodos log, debug, etc.  La Closure
	 * debe contener una llamada al println o al método que se
	 * desee del script invocado por jenkins.
	 * P. ej.: 
	 * // dentro de un script .groovy invocado directamente por jenkins
	 * def LogUtils log = new LogUtils();
	 * log.initLogger({println it});
	 */
	def initLogger( Closure _logger ) {
		nivelLog = 1
		logger = _logger
		release = "false"
		stream = ""
	} 
	
	// log de la clase haciendo uso de closure
	def log(def msg) {
	  if (logger != null && nivelLog>=1) {
	  	logger(msg)
	  }
	}
	
	def debug(def msg) {
	  if (logger != null && nivelLog>=2) {
	  	logger(msg)
	  }
	}
	
	def error(def msg) {
	  if (logger != null) {
	  	logger(msg)
	  }
	}

}