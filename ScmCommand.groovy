package es.eci.utils

import es.eci.utils.base.Loggable
import es.eci.utils.commandline.CommandLineHelper


class ScmCommand extends Loggable {

	//------------------------------------------------------------------------
	// Constantes de la clase
	
	// Para llamar directamente al proceso java
	private static final String USE_NATIVE_FRONT_END = "LSCM_USE_JAVA_FEC";

	//------------------------------------------------------------------------
	// Propiedades de la clase
	
	// Resultado de la última ejecución
	private int lastResult = 0;
	// Salida de error de la última ejecución
	private String errorOutput = null;
	
	//------------------------------------------------------------------------
	// Métodos de la clase
	
	public static class Commands {
		public static final Commands SCM = new Commands("scm");
		public static final Commands LSCM = new Commands("lscm");
		
		private String comando;
		
		private Commands(String comando) {
			this.comando = comando;
		}
		
		public String toString() {
			String ret = comando
			if (System.properties['os.name'].toLowerCase().contains('windows')) {
				if (comando == "scm")
					ret += ""
				else
					ret +=".bat"
			} else {
				if (comando == "scm")
					ret += ".sh"
			}
			return ret
		}
	}
	
	private Commands comando;
	private String scmToolsHome;
	private String daemonConfigDir;
	private File tempDir;

	/**
	 * Construye el comando tomando del build los valores de los directorios home del cliente RTC
	 * y de daemons.  Intenta obtenerlos respectivamente de las variables de entorno
	 * SCMTOOLS_HOME y DAEMONS_HOME.  Si es posible y existe la variable build, los obtiene
	 * del entorno de jenkins; si no, intenta leerlos como variables de entorno del SO 
	 * subyacente.
	 * @param comando Comando SCM
	 */
	public ScmCommand(Commands comando) {
		this.comando = comando;
		setup(comando, null, null);
	}	
	
	/**
	 * Construye un ScmCommand totalmente configurado
	 * @param comando Comando SCM
	 * @param scmtToolshome Home del comando scm
	 * @param daemonConfigDir Directorio de origen para daemons
	 */
	public ScmCommand(ScmCommand.Commands comando, String scmtToolshome, String daemonConfigDir) {
		setup(comando, scmtToolshome, daemonConfigDir);
	}
	
	/**
	 * Construye un ScmCommand totalmente configurado
	 * @param light Si es cierto, usa LSCM; si no, SCM
	 * @param scmtToolshome Home del comando scm
	 * @param daemonConfigDir Directorio de origen para daemons
	 */
	public ScmCommand(boolean light, String scmtToolshome, String daemonConfigDir) {
		setup(light?Commands.LSCM:Commands.SCM, scmtToolshome, daemonConfigDir)
	}
	
	// Constructor vacío
	public ScmCommand() {
		setup(Commands.SCM)
	}
	
		
	/*
	 * Construye un ScmCommand totalmente configurado
	 * @param comando Comando SCM
	 * @param scmtToolshome Home del comando scm
	 * @param daemonConfigDir Directorio de origen para daemons
	 */
	private void setup(Commands comando,String scmtToolshome, String daemonConfigDir) {
		this.scmToolsHome = scmtToolshome
		this.daemonConfigDir = daemonConfigDir
		def empty = { it == null || it == '' }
		if (this.scmToolsHome == null) {
			try {
				// Intentar leer la configuración de jenkins
				def build = Thread.currentThread().executable
				this.scmToolsHome = build.getEnvironment(null).get("SCMTOOLS_HOME")
			}
			catch (Exception e) {
				// Intentar leerla de variables de entorno
				def env = System.getenv()
				this.scmToolsHome = env["SCMTOOLS_HOME"]
			}
		}
		if (this.daemonConfigDir == null) {
			try {
				// Intentar leer la configuración de jenkins
				def build = Thread.currentThread().executable
				this.daemonConfigDir = build.getEnvironment(null).get("DAEMONS_HOME")
			}
			catch (Exception e) {
				// Intentar leerla de variables de entorno
				def env = System.getenv()
				this.daemonConfigDir = env["DAEMONS_HOME"]
			}
		}
		if (empty(this.scmToolsHome) || empty(this.daemonConfigDir)) {
			throw new Exception("Por favor, defina las variables de entorno SCMTOOLS_HOME y DAEMONS_HOME")
		}
		this.comando = comando
	}

	// Calcula el fragmento del comando dedicado a configuración dependiente
	//	del tipo de comando: pesado o ligero
	private String configFragment(File baseDir) {
		String fragmentoConfig = null;
		if (Commands.SCM == this.comando) {
			tempDir = File.createTempFile("rtc", "tmp")
			tempDir.delete()
			tempDir.mkdir()
			log "Utilizando el directorio temporal de metadatos ${tempDir.getCanonicalPath()}..."
			fragmentoConfig = "--config ${tempDir.getCanonicalPath()}"
		}
		else {
			// Utiliza el directorio de configuración de daemons
			String tempDaemonDir = decidirDirectorioDaemon(baseDir)
			log "Utilizando el directorio de metadatos de daemon ${tempDaemonDir}..."
			fragmentoConfig = "--config \"${tempDaemonDir}\""
			new File(tempDaemonDir).mkdirs()
		}
		return fragmentoConfig
	}
	
	/**
	 * Lanza un comando de RTC contra el servidor indicado.
	 * 
	 * Por ejemplo, "list components ..."
	 * o "load ..."
	 * @param comando Comando a ejecutar (sin el ejecutable SCM)
	 * @param usr Usuario RTC
	 * @param pwd Password RTC
	 * @param urlRepo URL del repositorio RTC
	 * @param baseDir Directorio de ejecución
	 * @return Salida del comando RTC
	 */
	public String ejecutarComando(String comando, String usr, String pwd, String urlRepo, File baseDir) {
		String cadena = "\"${this.scmToolsHome}/${this.comando.toString()}\" ${this.configFragment(baseDir)} " + comando + (urlRepo!=null?" -r ${urlRepo} ":"") + " -u ${usr} -P ${pwd}";
		CommandLineHelper helper = buildCommandHelper(cadena)
		helper.initLogger(this)		
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		if (Commands.SCM == this.comando) {
			log "Limpiando el directorio temporal de metadatos ${tempDir.getCanonicalPath()}..."
			tempDir.deleteDir()
		}	
		// Leer la salida del fichero
		return helper.getStandardOutput()
	}
	
	/**
	 * Método que genera el directorio en el que se va a mantener la sesion de RTC y se loga.
	 * @param usr usuario RTC
	 * @param pwd password RTC
	 * @param urlRepo URL del repositorio RTC
	 * @param baseDir Directorio base sobre el que se abre sesión.  Determina el fragmento de configuración
	 * @param alias Identificador de sesión, elegido por el usuario
	 * @return Fragmento de configuración para repetir en las llamadas sucesivas
	 */
	public String iniciarSesion(String usr, String pwd, String urlRepo, File baseDir, String alias) {
		String fragmentoConfig = configFragment(baseDir)
		String cadena = "\"${this.scmToolsHome}/${this.comando.toString()}\" ${fragmentoConfig} login -r ${urlRepo} -u ${usr} -P ${pwd} -n ${alias}"
		
		CommandLineHelper helper = buildCommandHelper(cadena)
		helper.initLogger(this)		
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		
		return fragmentoConfig
	}
	
	// Instancia un helper, asignándole las variables de entorno adecuadas
	private CommandLineHelper buildCommandHelper(String command) {
		CommandLineHelper helper = new CommandLineHelper(command);
		helper.setEnvVar(USE_NATIVE_FRONT_END, "false");
		return helper
	}
	
	/**
	 * Método que ejecuta comando con persistencia en sesión, identificada por el alias
	 * @param comando Comando a ejecutar (sin autenticación, configuración ni repositorio)
	 * @param fragmentoConfig Fragmento de configuración devuelto por el método 'iniciarSesion'
	 * @param alias Identificador de sesión, elegido por el usuario
	 * @return Salida del comando RTC
	 */
	public String ejecutarComando(String comando, String fragmentoConfig, String alias, File baseDir) {
		String cadena = "\"${this.scmToolsHome}/${this.comando.toString()}\" $fragmentoConfig " + comando + " -r ${alias}"
		CommandLineHelper helper = buildCommandHelper(cadena);
		helper.initLogger(this)		
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		// Leer la salida del comando
		return helper.getStandardOutput()
	}
	
	/**
	 * Devuelve el resultado de la última ejecución
	 * @return Resultado de la última ejecución
	 */
	public int getLastResult() {
		return lastResult;
	}
	
	/**
	 * Devuelve la salida de error del último comando, caso de producirse
	 * @return Salida de error
	 */
	public String getLastErrorOutput() {
		return errorOutput;
	}
	
	/**
	 * Método que hace logout de RTC y borra la carpeta que mantiene la sesión
	 * @param fragmentoConfig Fragmento de configuración de esta sesión
	 * @param baseDir Directorio de ejecución
	 * @param alias Identificador de sesión, elegido por el usuario
	 */
	public void cerrarSesion(String fragmentoConfig, File baseDir, String alias) {
		String cadena = "\"${this.scmToolsHome}/${this.comando.toString()}\" $fragmentoConfig logout -r ${alias}"
		
		CommandLineHelper helper = buildCommandHelper(cadena)
		helper.initLogger(this)		
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		
		if (Commands.SCM == this.comando) {
			log "Limpiando el directorio temporal de metadatos ${tempDir.getCanonicalPath()}..."
			tempDir.deleteDir()
		}
	}

	// Localiza el directorio adecuado para la configuración del daemon
	private String decidirDirectorioDaemon(File baseDir) {
		// Utiliza el directorio de configuración de daemons
		String path = baseDir.getCanonicalPath();
		if (System.properties['os.name'].toLowerCase().contains('windows')) {
			// Limpiar la letra de la unidad
			path = path.replaceAll(/\w\:/, "")
		}
		return daemonConfigDir + path
	}
	
	/**
	 * Este método intenta arrancar un demonio RTC en la ruta indicada
	 * @param baseDir Sandbox en el que se ejecuta el demonio
	 * @return Salida del comando
	 */
	public String arrancarDemonio(File baseDir) {
		
		String cadena = "\"${this.scmToolsHome}/${Commands.SCM.toString()}\" ${configFragment(baseDir)} daemon start "
		
		CommandLineHelper helper = buildCommandHelper(cadena)
		helper.initLogger(this)		
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		
		return helper.getStandardOutput()
	}
	
	/**
	 * Este método intenta detener un demonio RTC en la ruta indicada
	 * @param baseDir Sandbox en el que se ejecuta el demonio
	 * @return Salida del comando
	 */
	public String detenerDemonio(File baseDir) {
		
		String cadena = "\"${this.scmToolsHome}/${Commands.SCM.toString()}\" ${configFragment(baseDir)} daemon stop -a "
		
		CommandLineHelper helper = buildCommandHelper(cadena)
		helper.initLogger(this)		
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		
		return helper.getStandardOutput()
	}


}