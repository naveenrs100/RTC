package es.eci.utils
import java.util.List;

import es.eci.utils.base.Loggable
import groovy.json.JsonSlurper

/**
 * Esta clase implementa el código para averiguar la versión de un componente
 * utilizando el método de líneas base solicitado por el equipo de centros
 * comerciales.
 * 
 * Con este método, la última línea base definida sobre el componente determina
 * su versionado a la manera de como lo haría el sistema de dependencias de maven.
 * Así, un componente cuya última línea base responda al patrón
 * /[0-9]+.[0-9]+.[0-9]+(\.[0-9]+)?\-SNAPSHOT/
 * tendría versión abierta, y uno que tenga una última línea base
 * /[0-9]+.[0-9]+.[0-9]+(\.[0-9]+)?/
 * diremos que tiene versión cerrada.
 * 
 * Si la última línea base no responde a estos dos patrones, el método debe devolver un error  
 */
class ComponentVersionHelper extends Loggable {
	
	//-------------------------------------------------------------------
	// Constantes de la clase
	
	final static def REGEXP_CERRADA = /[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?/
	final static def REGEXP_ABIERTA =  /[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?\-SNAPSHOT/
	
	//-------------------------------------------------------------------
	// Propiedades de la clase
	
	private String scmToolsHome;
	
	
	//-------------------------------------------------------------------
	// Métodos de la clase
	
	public ComponentVersionHelper() {}
	
	// Constructor sin argumentos
	// daemonsConfigDir: obsoleto
	@Deprecated
	public ComponentVersionHelper(String scmToolsHome, String daemonsConfigDir) {
		this.scmToolsHome = scmToolsHome;
	}
	
	public ComponentVersionHelper(String scmToolsHome) {
		this.scmToolsHome = scmToolsHome;
	}
	
	/**
	 * Este método devuelve las líneas base (etiquetas) de un componente, ordenadas de más a
	 * menos reciente, para una determinada corriente.
	 * @param component Nombre del componente RTC
	 * @param stream Nombre de la corriente o workspace RTC
	 * @param user Usuario RTC
	 * @param password Clave del usuario
	 * @param repositorio URL del repositorio RTC
	 * @return Líneas base del componente.
	 */
	public List<String> getBaselines(String component, String stream, String user, String password, String repository) {
		List<String> ret = new LinkedList<>();
		TmpDir.tmp { File daemonsConfigDir ->
			ScmCommand scm = new ScmCommand(ScmCommand.Commands.SCM, 
				scmToolsHome, daemonsConfigDir.getCanonicalPath());
			scm.initLogger(this)
			String salida = null;
			TmpDir.tmp { File baseDir ->
				salida = scm.ejecutarComando("list baseline -j -C \"${component}\" -w \"${stream}\" ", 
					user, password, repository, baseDir)
				log(salida)
			}
			if (scm.getLastResult() != 0) {
				throw new Exception("Error: " + scm.getLastResult());	
			}
			
			def json = new JsonSlurper().parseText(salida);
			json.each { def comp ->
				comp.baselines.each { def baseline ->
					ret.add(baseline.name)
				}
			}
		}
		return ret;
	}
	
	/**
	 * Devuelve la lista de instantáneas (etiquetas) hechas sobre una corriente, 
	 * ordenadas de más a menos reciente.  Solo las 20 primeras.
	 * @param stream Nombre de la corriente o workspace RTC
	 * @param user Usuario RTC
	 * @param password Clave del usuario
	 * @param repository URL del repositorio RTC
	 * @return Lista de instantáneas hechas sobre la corriente.
	 */
	public List<String> getSnapshots(String stream, String user, String password, String repository) {
		List<String> ret = new LinkedList<>();
		TmpDir.tmp { File daemonsConfigDir ->
			ScmCommand scm = new ScmCommand(ScmCommand.Commands.SCM, 
				scmToolsHome, daemonsConfigDir.getCanonicalPath());
			scm.initLogger { this }
			
			String salida = null;
			TmpDir.tmp { File baseDir ->
				salida = scm.ejecutarComando("list snapshots -m 20 -j \"${stream}\" ", 
					user, password, repository, baseDir)
				log(salida)
			}
			
			def json = new JsonSlurper().parseText(salida);
			json.snapshots.each { def snapshot ->
				ret.add(snapshot.name);
			}
		}
		
		return ret;
	}
	
	/**
	 * Devuelve la lista de componentes contenidos en un workspace de RTC
	 * @param baseDir Directorio base para lanzar comandos
	 * @param stream Corriente RTC
	 * @param userRTC Usuario RTC
	 * @param pwdRTC Password RTC
	 * @param urlRTC URL de repositorio RTC
	 * @return Lista de nombres de componente
	 */
	public List<String> getComponents(
			File baseDir,
			String stream, 
			String userRTC, 
			String pwdRTC, 
			String urlRTC) {
		List<String> ret = new LinkedList<String>()
		TmpDir.tmp { File daemonsConfigDir ->
			ScmCommand command = new ScmCommand(
				ScmCommand.Commands.SCM, scmToolsHome, 
				daemonsConfigDir.getCanonicalPath());
			command.initLogger(this)
			String output = command.ejecutarComando(
				"list components \"${stream}\" -j ", 
				userRTC, 
				pwdRTC, 
				urlRTC, 
				baseDir)
			def json = new JsonSlurper().parseText(output)
			json.workspaces[0].components.each { component ->
				ret << component.name
			}
		}
		return ret;
	}
	
	/**
	 * Devuelve la versión de un componente definida como su última línea base
	 * @param component Nombre del componente RTC
	 * @param stream Nombre de la corriente o workspace RTC
	 * @param user Usuario RTC
	 * @param password Clave del usuario
	 * @param repository URL del repositorio RTC
	 * @return Versión del componente, si la última línea base definida tiene el 
	 * formato que necesitamos.  Null si no tiene líneas base.
	 */
	public String getVersion(String component, String stream, String user, String password, String repository) {
		List<String> baselines = getBaselines(component, stream, user, password, repository);
		String ret =null;
		if (baselines?.size() > 0) {
			ret = baselines[0]
		}
		return ret;
	}
	
	/** 
	 * Indica si la cadena pasada se corresponde con una línea base abierta.
	 * @param s Cadena a examinar
	 * @return Cierto si se corresponde con una versión abierta
	 */
	public boolean esAbierta(String s) {
		boolean ret = false;
		if (s != null && s.trim().length() > 0) {
			ret = (s ==~ REGEXP_ABIERTA)
		}
		return ret;
	}
	
	/** 
	 * Indica si la cadena pasada se corresponde con una línea base cerrada.
	 * @param s Cadena a examinar
	 * @return Cierto si se corresponde con una versión cerrada
	 */
	public boolean esCerrada(String s) {
		boolean ret = false;
		if (s != null && s.trim().length() > 0) {
			ret = (s ==~ REGEXP_CERRADA)
		}
		return ret;
	}
}
