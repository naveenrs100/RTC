package es.eci.utils
import es.eci.utils.base.Loggable
import groovy.io.FileType
import groovy.xml.XmlUtil


/**
 * Esta clase debe bajar el componente del catálogo a un directorio temporal, 
 * encontrar ahí los ficheros .env correspondientes y decidir a partir de los mismos
 * qué componentes se consideran bibliotecas en el entorno actual.
 */
class EnvironmentCatalog extends Loggable {
	

	public EnvironmentCatalog() {}
	
	public EnvironmentCatalog(Closure log) {
		initLogger(log)
	}
	
	/**
	 * Este método actualiza el directorio /jenkins/entornosCompilacion/${streamSinDesarrollo}/catalogo/
	 * con los respectivos ficheros .env
	 * @param stream Nombre de corriente RTC
	 * @param component Nombre de componente RTC (debe ser el que contiene el entorno de 
	 * compilación, normalmente será CCCC-environmentCatalogoC
	 * @param repository Repositorio RTC
	 * @param user Usuario RTC
	 * @param password Password RTC
	 */
	public void updateCatalog(String stream, String component, String repository, String user, String password) {
		Date dInicio = new Date()
		String streamSinDesarrollo = stream
		if (streamSinDesarrollo.endsWith("-DESARROLLO")) {
			streamSinDesarrollo = streamSinDesarrollo.replaceAll("-DESARROLLO", "")
		}
		File dir = new File("/jenkins/entornosCompilacion/${streamSinDesarrollo}/catalogo/")
		if (dir.exists()) {
			ScmCommand scm = new ScmCommand()
			scm.initLogger { this }
			Date now = new Date()
			String timestamp = Long.toString(now.getTime())
			String ws = stream + timestamp
			log("Refrescando el catálogo")
			log("Descargando ${stream}, comp. ${component} al directorio ${dir.canonicalPath} ...")
			// Crear ws temporal
			scm.ejecutarComando("create workspace -e \"${ws}\"", user, password, repository, dir)
			// Añadir el componente al ws temporal
			scm.ejecutarComando("workspace add-components \"${ws}\" \"${component}\" -s \"${stream}\"", user, password, repository, dir)
			// Descargar el componente
			scm.ejecutarComando("load \"${ws}\" -d \"${dir.canonicalPath}\"  \"${component}\" --force ", user, password, repository, dir)
			// Eliminar ws temporal
			scm.ejecutarComando("workspace delete \"${ws}\" ", user, password, repository, dir)
		}
		Date dFinal = new Date()
		log ("Tiempo total para refrescar el catálogo " + (dFinal.getTime() - dInicio.getTime()) + " mseg.")
	}
	
	/**
	 * Este método devuelve los nombres de los componentes de biblioteca en la corriente
	 * y componente solicitado.
	 * @param stream Nombre de corriente RTC
	 * @param component Nombre de componente RTC (debe ser el que contiene el entorno de 
	 * compilación, normalmente será CCCC-environmentCatalogoC
	 * @param repository Repositorio RTC
	 * @param user Usuario RTC
	 * @param password Password RTC
	 * @returns Lista de nombres de componente incluidos en los ficheros .env como bibliotecas de la corriente
	 */
	public List<String> getLibraries(String stream, String component, String repository, String user, String password) {
		List<String> ret = []
		Date dInicio = new Date()
		try {
			TmpDir.tmp { File dir -> 
				log ("Parseando el entorno de compilación sobre el directorio " + dir.getCanonicalPath())
				
				// Crea un ws temporal y lo utiliza para descargar el código
				Date now = new Date()
				String timestamp = Long.toString(now.getTime())
				String ws = stream + timestamp
				ScmCommand scm = new ScmCommand(ScmCommand.Commands.LSCM)
				scm.initLogger { this }
				try {
					// Crear ws temporal
					scm.ejecutarComando("create workspace -e \"${ws}\"", user, password, repository, dir)
					// Añadir el componente al ws temporal
					scm.ejecutarComando("workspace add-components \"${ws}\" \"${component}\" -s \"${stream}\"", user, password, repository, dir)
					// Descargar el componente
					scm.ejecutarComando("load \"${ws}\" -d \"${dir.canonicalPath}\"  \"${component}\" --force", user, password, repository, dir)
					// Eliminar ws temporal
					scm.ejecutarComando("workspace delete \"${ws}\"", user, password, repository, dir)
					
					// Busca mediante un traverse ficheros .env y los procesa
					dir.traverse (type : FileType.FILES, maxDepth: -1) { fichero ->
						def nombre = fichero.getName().toLowerCase()
						if (nombre.endsWith(".env")) {
							// Fichero de entorno
							def xml = new XmlParser().parse(fichero)
							xml.lib.each { lib ->
								def componente = lib.componente.text()
								if (!ret.contains(componente)) {
									ret << componente
								}
							}
						}
					}
				}
				finally {
					log("Intentando detener el demonio sobre ${dir.canonicalPath} ...")
					scm.detenerDemonio(dir)
				}
			}
		}
		catch (Exception e) {
			log e.getMessage()
			ret = []
		}
		Date dFinal = new Date()
		log ("Tiempo total para parsear el entorno de compilación " + (dFinal.getTime() - dInicio.getTime()) + " mseg.")
		return ret
	}
	
	/**
	 * Este método indica a qué entorno (compila o compila_sf) pertenece una 
	 * biblioteca en versión abierta.  Notar que si la biblioteca está declarada
	 * en versión cerrada, no se refresca.
	 * @param stream Corriente de Servidor C
	 * @param component Componente de catálogo
	 * @param repository Repositorio RTC
	 * @param library Biblioteca que buscamos en RTC
	 * @param user Usuario RTC
	 * @param password Password RTC
	 * @returns Entorno o entornos a los que pertenece la biblioteca.
	 */
	public List<String> getEnvironments(String stream, String component, String library, String repository, String user, String password) {
		log("getEnvironments: stream <- ${stream} ")
		log("getEnvironments: component <- ${component} ") 
		log("getEnvironments: library <- ${library} ")
		log("getEnvironments: repository <- ${repository} ")
		log("getEnvironments: user <- ${user} ")
		def ret = []
		long tiempo = Stopwatch.watch {
			try {
				TmpDir.tmp { File dir -> 
					log ("Descargando el entorno de compilación sobre el directorio " + dir.getCanonicalPath())					
					// Crea un ws temporal y lo utiliza para descargar el código
					Date now = new Date()
					String timestamp = Long.toString(now.getTime())
					String ws = stream + timestamp
					ScmCommand scm = new ScmCommand(ScmCommand.Commands.LSCM)
					scm.initLogger { this }
					try {
						// Crear ws temporal
						scm.ejecutarComando("create workspace -e \"${ws}\"", user, password, repository, dir)
						// Añadir el componente al ws temporal
						scm.ejecutarComando("workspace add-components \"${ws}\" \"${component}\" -s \"${stream}\"", user, password, repository, dir)
						// Descargar el componente
						log("Cargando el catálogo...")
						scm.ejecutarComando("load \"${ws}\" -d \"${dir.canonicalPath}\"  \"${component}\" --force", user, password, repository, dir)
						log("Cargado el catálogo:")
						dir.listFiles().each { File fCat ->
							log fCat.getCanonicalPath()
						}
						
						// Busca mediante un traverse ficheros .env y los procesa
						dir.traverse (type : FileType.FILES, maxDepth: -1) { fichero ->
							def nombre = fichero.getName().toLowerCase()
							String entorno = null;
							if (nombre == "compila_cc.env") {
								entorno = "compila"
							}
							else if (nombre == "compila_cc.sf.env") {
								entorno = "compila_sf"
							}
							if (entorno != null) {
								log("Verificando entorno $entorno ...")
								// Fichero de entorno
								def xml = new XmlParser().parse(fichero)
								xml.lib.each { lib ->
									def componente = lib.componente.text()
									def version = lib.version.text()
									if (version.endsWith("-SNAPSHOT") && componente == library) {
										ret << entorno
									}
								}
							}
						}	
					}
					finally {						
						// Eliminar ws temporal
						scm.ejecutarComando("workspace delete \"${ws}\"", user, password, repository, dir)	
						scm.detenerDemonio(dir)
					}
				}
			}
			catch (Exception e) {
				log e.getMessage()
				ret = []
			}
		}
		log ("Tiempo consumido en identificar el entorno: $tiempo mseg")
		return ret
	}
	
	/**
	 * Salida del comando status de SCM.  Normalmente algo así:
	 * Workspace: (1038) "ws1234325" <-> (1039) "GIS - Proyecto Prueba Release - DESAR>
		  Component: (1034) "PruebaRelease - App 1" <-> (1039) "GIS - Proyecto Prueba R>
		    Baseline: (1035) 9 "1.0.40.0-SNAPSHOT-build:14"
		    Outgoing:
		      Change sets:
		        (1040)  *--@  <No comment>
		          Changes:
		            ---c- \pom.xml
	 * @param status Salida del comando
	 * @return Id del cambio abierto
	 */
	private String parseStatus(String status) {
		String id = null
		status.eachLine { line ->
			if (line.contains("@")) {
				// Busca texto entre paréntesis
				def pattern = /\(([^\)]*)\)/
				def matcher = line =~ pattern
				if (matcher.size() > 0 && matcher[0].size() > 0) {
					// El primero
					id = matcher[0][1]
				}
			}
		}
		return id
	}
	
	/**
	 * Este método cierra la versión de una biblioteca en el fichero de entorno
	 * de compilación
	 * @param stream Corriente de Servidor C
	 * @param component Componente de catálogo
	 * @param library Nombre de la biblioteca
	 * @param version Versión a la que queremos cerrar la biblioteca
	 * @param environments Entornos que se deben actualizar (compila 
	 * 	o bien compila_sf)
	 * @param workItem workItem para subir versión
	 * @param repository URL del repositorio RTC
	 * @param user Usuario del repositorio RTC
	 * @param password Clave del usuario en RTC
	 */
	public void closeVersion(String stream, String component, String library, String version, List<String> environments, String workItem, String repository, String user, String password) {
		log "Cierre de versión:"
		log "stream : $stream "
		log "component : $component "
		log "library : $library "
		log "version : $version "
		log "environments : $environments "
		log "workItem : $workItem "
		log "repository : $repository "
		log "user : $user "
		log "password : $password "
		long tiempo = Stopwatch.watch {
			try {
				TmpDir.tmp { File dir -> 
					log ("Cerrando el entorno de compilación sobre el directorio " + dir.getCanonicalPath())					
					// Crea un ws temporal y lo utiliza para descargar el código
					Date now = new Date()
					String timestamp = Long.toString(now.getTime())
					String ws = stream + timestamp
					ScmCommand scm = new ScmCommand(ScmCommand.Commands.LSCM)
					scm.initLogger { this }
					def MI_ALIAS = "repo"
					try {
						scm.iniciarSesion(user, password, repository, dir, MI_ALIAS)
						// Crear ws temporal
						scm.ejecutarComando("create workspace -e \"${ws}\" -s \"${stream}\"", dir, MI_ALIAS)
						// Añadir el componente al ws temporal
						scm.ejecutarComando("workspace add-components \"${ws}\" \"${component}\" -s \"${stream}\"", dir, MI_ALIAS)
						// Descargar el componente
						scm.ejecutarComando("load \"${ws}\" -d \"${dir.canonicalPath}\" \"${component}\" --force", dir, MI_ALIAS)
						
						// Alterar la versión
						// Buscar el fichero en el directorio
						def identificarFichero = { File fichero ->
							boolean ret = false;
							if (environments.contains('compila')) {
								ret = (fichero.getName() == 'compila_cc.env')
							}
							if (environments.contains('compila_sf')) {
								ret = (fichero.getName() == 'compila_cc.sf.env')
							}
							return ret
						}
						
						boolean hayCambios = false
						// Busca mediante un traverse ficheros .env y los procesa
						log "Cerrando versiones de las bibliotecas abiertas en ${component}..."
						dir.traverse (type : FileType.FILES, maxDepth: -1) { fichero ->
							boolean hayCambiosFichero = false
							// Si es el fichero de entorno
							if (identificarFichero(fichero)) {
								// Parsearlo y buscar la biblioteca
								def xml = new XmlParser().parse(fichero)
								xml.lib.each { lib ->
									def componente = lib.componente.text()
									def versionC = lib.version.text()
									if (versionC.endsWith("-SNAPSHOT") && componente == library) {
										// Informar de que se cierra la versión
										log("Cerrando la biblioteca ${library} a la versión ${version}")
										// Actualizar la versión
										lib.version[0].setValue(version)
										hayCambios = true
										hayCambiosFichero = true
									}
								}
								if (hayCambiosFichero) {
									// Actualizarlo
									log XmlUtil.serialize(xml)
									log "Escribiendo los cambios a ${fichero.canonicalPath} ..."
									XmlUtil.serialize(xml, new FileWriter(fichero))
								}
							}
						}	
						
						if (hayCambios) {
							log "checkin ..."
							log scm.ejecutarComando("checkin . ", dir, null)
							log "status -B -C  ..."
							String status = scm.ejecutarComando("status -B -C ", dir, null)
							log status
							String changeSet = parseStatus(status)
							// ---> leer el id del cambio - XXX
							log "changeset comment $changeSet \"C AIX - Cierre de versión\" ..."
							log scm.ejecutarComando("changeset comment $changeSet \"C AIX - Cierre de versión\"", dir, MI_ALIAS)
							log "changeset associate $changeSet $workItem ..."
							log scm.ejecutarComando("changeset associate $changeSet $workItem", dir, MI_ALIAS)
							log "changeset close $changeSet ..."
							log scm.ejecutarComando("changeset close $changeSet", dir, MI_ALIAS)
							log "deliver $changeSet ..."
							log scm.ejecutarComando("deliver $changeSet", dir, MI_ALIAS)						
						}	
					}
					finally {
						// Eliminar ws temporal
						log "workspace delete \"${ws}\" ..."
						log scm.ejecutarComando("workspace delete \"${ws}\"", dir, MI_ALIAS)
						log "cerrando sesión $MI_ALIAS ..."
						log scm.cerrarSesion(dir, MI_ALIAS)
						log "deteniendo el demonio sobre ${dir.canonicalPath} ..."
						log scm.detenerDemonio(dir)
					}
				}
			}
			catch (Exception e) {
				log e.getMessage()
				throw e
			}
		}
		log ("Tiempo consumido en cerrar la versión: $tiempo mseg")
	}
}