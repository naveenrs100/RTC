package es.eci.utils

import java.io.File;
import java.util.List;
import java.util.Map;

import es.eci.utils.base.Loggable;
import es.eci.utils.commandline.CommandLineHelper;
import es.eci.utils.pom.MavenCoordinates
import groovy.lang.Closure;

import java.nio.charset.Charset;

import groovy.io.FileVisitResult;
import groovy.xml.*;
import components.MavenComponent;
import groovy.json.JsonSlurper;

class GitBuildFileHelper extends Loggable {
	
	// Nombre del reactor eci
	private static final String REACTOR_FILE_NAME = "reactor_sort_jobs"
	
	// Tecnologías soportadas por el parser
	private static Map tecnologias = ['maven': 'pom.xml', 'gradle': 'build.gradle']

	//	se lanzan sobre directorio temporal aparte de éste
	private File parentWorkspace;

	// Acción del proceso de IC (build, deploy, release, addFix, addHotfix)
	private String action;

	/**
	 * Constructor del helper	 
	 * @param log Closure para logging
	 */
	public GitBuildFileHelper(String action, File parentWorkspace, Closure log = null) {
		this.action = action
		this.parentWorkspace = parentWorkspace
		if (log != null) {
			initLogger(log)
		}
	}

	/**
	 * Indica si el helper soporta determinada tecnología
	 * @param technology Tecnología por la que se pregunta
	 * @return Cierto si el helper es capaz de interpretar buildFiles de esa
	 * tecnología
	 */
	public static boolean supportsTechnology(String technology) {
		return tecnologias.keySet().contains(technology)
	}

	/**
	 * Este método parsea los módulos si existen en el fichero de construcción
	 * @param currentModule Módulo actual
	 * @param buildFile Fichero de construcción
	 * @param technology Tecnología de construcción
	 */
	private List<String> parseModules(String currentModule, File buildFile, String technology) {
		List<String> ret = []
		if (tecnologias.keySet().contains(technology)) {
			if ("maven"==technology) {
				log("Parsing ${buildFile}")
				def pom = new XmlSlurper(false, false).parse(buildFile)
				if (pom.name != null && pom.name.text() != null && pom.name.text().trim() != '') {
					// Igualar el atributo name al artifactId
					pom.name.replaceBody(pom.artifactId.text());
					String text = XmlUtil.serialize(pom)
					Writer writer = new OutputStreamWriter(new FileOutputStream(buildFile), Charset.forName("UTF-8"))
					writer.write(text, 0, text.length())
					writer.flush()
				}
				if (pom.modules != null) {
					pom.modules.module.each { module ->
						ret << buildFileName(currentModule, module.text()) + "/"
					}
				}
			}
		}
		return ret
	}

	/**
	 * Compone el nombre del fichero
	 * @param arguments
	 * @return
	 */
	private String buildFileName(String... arguments) {
		StringBuilder sb = new StringBuilder();
		int contador = 0;
		arguments.eachWithIndex { arg, i ->
			if (arg != null && arg.trim().length() > 0) {
				if (contador > 0) {
					sb.append("/")
				}
				sb.append(arg.trim())
				contador ++
			}
		}
		return sb.toString()
	}

	/**
	 * Limpia restos de git del directorio
	 * @param directory
	 */
	private void cleanRTC(File directory) {
		if (directory != null && directory.exists() && directory.isDirectory()) {
			def remove = { dir ->
				File eliminar = new File(directory, dir)
				eliminar.deleteDir()
			}
			[ ".git" ].each { dir -> remove(dir) }
		}
	}

	/**
	 * Crea el reactor de la corriente
	 * @param jobs Lista de componentes
	 * @param home Directorio temporal donde se ha bajado los pom.xml
	 */
	private void createParentReactor(List<String> components, File home) {
		def parentReactor = new File("${home.canonicalPath}/pom.xml")
		parentReactor.createNewFile()
		parentReactor.delete()
		parentReactor << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
		parentReactor << "\t<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
		parentReactor << "\txsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
		parentReactor << "\t\t<modelVersion>4.0.0</modelVersion>\n"

		parentReactor << "\t\t<groupId>es.eci.reactor</groupId>\n"
		parentReactor << "\t\t<artifactId>${REACTOR_FILE_NAME}</artifactId>\n"
		parentReactor << "\t\t<version>1.0.0</version>\n"

		parentReactor << "\t\t<packaging>pom</packaging>\n"
		parentReactor << "\t\t<modules>\n"
		components.each { component ->
			if (new File("${home.canonicalPath}/${component}/pom.xml").exists()) {
				parentReactor << "\t\t\t<module>${component}</module>\n"
			}
		}
		parentReactor << "\t\t</modules>\n"
		parentReactor << "\t</project>\n"
	}

	/**
	 * Devuelve el artefacto en forma de clase ArtifactBeanLight
	 * @param pom Estructura XML del fichero
	 * @param baseDirectory Directorio base de la jerarquía de pom.xml
	 * @return
	 */
	def getArtifactMaven(pom, File baseDirectory){
		ArtifactBeanLight artifact = new ArtifactBeanLight()
		artifact.version = pom.version.text()
		if (artifact.version==null || artifact.version.length()==0) {
			artifact.version = pom.parent.version.text()
		}
		artifact.version = new VersionUtils().solveRecursive(baseDirectory, pom, artifact.version)
		// Resolver la versión contra una propiedad si fuera necesario
		artifact.artifactId = pom.artifactId.text()
		artifact.groupId = pom.groupId.text()
		if (artifact.groupId==null || artifact.groupId.length()==0) {
			artifact.groupId = pom.parent.groupId.text()
		}
		return artifact
	}

	/**
	 * Devuelve la lista de artefactos maven en una serie de ficheros pom.xml
	 * @param ficheros Lista de ficheros a parsear
	 * @param baseDirectory Directorio base donde está la jerarquía de pom
	 * @return
	 */
	def getArtifactsMaven(Map<String, List<File>> ficheros, File baseDirectory) {
		def artifacts = [:]
		ficheros.each { fComp ->
			def comp = fComp.key
			def f = fComp.value
			def list = []
			f.each { fichero ->
				def pom = new XmlParser().parse(fichero)
				list.add(getArtifactMaven(pom, baseDirectory))
			}
			artifacts.put(comp,list)
		}
		return artifacts
	}

	/**
	 * Este método construye a partir de una lista de ficheros pom.xml
	 * el fichero json de artefactos necesario para poder lanzar los procesos
	 * de IC ordenados
	 * @param action Identificador de proceso IC (build, deploy, release, addFix, addHotfix)
	 * @param ficheros Tabla de ficheros pom.xml indexada por componente
	 * @param home Directorio de construcción del artifacts.json
	 * @param baseDirectory Directorio base del conjunto de poms
	 */
	public boolean processSnapshotMaven(String action, 
			Map<String, List<File>> ficheros, File home, File baseDirectory, List<String> finalComponentsList = null){
		def result = false
		def err = new StringBuffer()
		def artifactsComp = getArtifactsMaven(ficheros, baseDirectory)
		def artifacts = []
		artifactsComp.each { artsComp ->
			def a = artsComp.value
			a.each{ artifact ->
				artifacts.add(artifact)
			}
		}
		ficheros.each { fComp ->
			def comp = fComp.key
			def f = fComp.value
			f.each { fichero ->
				def pom = new XmlParser().parse(fichero)
				pom.dependencies.dependency.each { dependency ->
					def artifact = getArtifactMaven(dependency, baseDirectory)
					if (artifact.version!=null){
						if (artifact.version.toLowerCase().indexOf("snapshot")>0){
							if (artifacts.find{it.equals(artifact)}!=null){
								result = true
							}else{
								err << "There is a snapshot version in ${dependency.artifactId.text()} inside ${fichero}\n"
							}
						}
					}
				}
			}
		}
		if (err.length()>0) {
			if (['release', 'addFix', 'addHotfix'].contains(action)) {
				throw new NumberFormatException("${err}")
			}
		}
		// escribe artifacts para ser usado por stepFileVersioner.groovy para quitar los snapshots
		VersionUtils vUtils = new VersionUtils();
		vUtils.writeJsonArtifactsMaven(artifactsComp, home, "artifacts.json", finalComponentsList);
		vUtils.writeJsonArtifactsMaven(artifactsComp, home, "artifactsAll.json", null);		
		return result
	}

	/**
	 * Escribe el fichero de artefactos maven en el directorio de la construcción
	 * @param artifactsComp
	 * @param home
	 * @return
	 */
	def writeJsonArtifactsMaven(artifactsComp, File home){
		def file = new File("${home.canonicalPath}/artifacts.json")
		
		file.delete()
		
		file << "["
		
		StringBuilder buffer_components = new StringBuilder()
		
		artifactsComp.each { artsComp ->
			
			def component = artsComp.key
			def artifacts = artsComp.value
			
			StringBuilder buffer_artifacts = new StringBuilder()
			
			if (artifacts.size() > 0) {
				artifacts.each{ artifact ->
					buffer_artifacts.append("{\"version\":\"${artifact.version}\",\"component\":\"${component}\",\"groupId\":\"${artifact.groupId}\",\"artifactId\":\"${artifact.artifactId}\"},")
				}
				buffer_components.append(buffer_artifacts.substring(0, buffer_artifacts.length() - 1))
				buffer_components.append(",")
			}
		}
		
		file << buffer_components.substring(0, buffer_components.length() - 1)
		file << "]"
	}

	/**
	 * Este método crea, sobre el directorio base, la estructura de buildFiles
	 * correspondiente.  Por ejemplo
	 *
	 * baseDirectory/component1/pom.xml
	 * baseDirectory/component1/modulo1/pom.xml
	 * baseDirectory/component1/modulo2/pom.xml
	 * baseDirectory/component1/modulo2/subModulo1/pom.xml
	 * baseDirectory/component1/modulo2/subModulo2/pom.xml
	 * baseDirectory/component1/modulo2/subModulo3/pom.xml
	 * baseDirectory/component1/modulo3/pom.xml
	 * baseDirectory/component2/pom.xml
	 * baseDirectory/component2/modulo1/pom.xml
	 * baseDirectory/component2/modulo2/pom.xml
	 * baseDirectory/component3/pom.xml
	 *
	 * Se responsabiliza de la creación en el home de la construcción del
	 * fichero artifacts.json, necesario para poder lanzar después el proceso de IC.
	 *
	 * @param baseDirectory Directorio base donde se encuentran los pom.xml de los componentes
	 * descargados.	
	 * @param components Lista de componentes. 
	 * @return lista de componentes ordenados
	 */
	public List<MavenComponent> createStreamReactor(
			File baseDirectory,
			List components,
			List<String> finalComponentsList = null) {
						
		// Construcción del reactor padre
		return buildArtifactsFile(components, baseDirectory, finalComponentsList)
	}

	/**
	 * Devuelve el listado de pom.xml bajo un directorio 
	 * @param fromDir
	 * @return (Array)files
	 */
	def getPoms(fromDir, String fileMatch = "pom\\.xml"){
		def files = []
		fromDir.traverse(
				type: groovy.io.FileType.FILES,
				preDir: { if (it.name.startsWith(".") || it.name == 'target') return FileVisitResult.SKIP_SUBTREE},
				nameFilter: ~/${fileMatch}/,
				maxDepth: -1
				){
					files << it
				}
		return files
	}

	/**
	 * Este método ejecuta mvn validate sobre el directorio, generando un fichero
	 * reactor.log cuyo contenido devuelve
	 * @param baseDir Directorio base
	 * @param maven Eejecutable maven
	 */
	public String validateReactor(File baseDir, String maven) {
		// mvn validate
		log("Se lanza el comando \"${maven} validate\" sobre el directorio \"${baseDir.getAbsolutePath()}\"")
		CommandLineHelper helper = new CommandLineHelper("${maven} validate");
		helper.initLogger(this)
		helper.execute(baseDir)
		return helper.getStandardOutput()
	}

	/**
	 * Este método crea, sobre el directorio base, la estructura de buildFiles
	 * correspondiente.  Por ejemplo
	 * baseDirectory/component/pom.xml
	 * baseDirectory/component/modulo1/pom.xml
	 * baseDirectory/component/modulo2/pom.xml
	 * baseDirectory/component/modulo2/subModulo1/pom.xml
	 * baseDirectory/component/modulo2/subModulo2/pom.xml
	 * baseDirectory/component/modulo2/subModulo3/pom.xml
	 * baseDirectory/component/modulo3/pom.xml
	 * ¡¡ATENCIÓN!! Este método deja un daemon sobre el directorio base, hay que matarlo después
	 * @param baseDirectory Directorio base
	 * @param workspaceRTC Workspace de repositorio sobre el que se trabaja
	 * @param component Componente a bajar
	 * @param userRTC Usuario RTC
	 * @param pwdRTC Password de usuario RTC
	 * @param urlRTC URL del repositorio RTC
	 */
	public File createBuildFileStructure(
			File baseDirectory,
			String component,
			String technology,
			String gitHost,
			String gitGroup,
			String branch,
			String gitCommand = null) {
		File dirComponente = null
		if (technology != null && tecnologias.keySet().contains(technology)) {
			dirComponente = new File(baseDirectory.getCanonicalPath() + System.getProperty("file.separator") + component)
			dirComponente.mkdirs()
			// Búsqueda en anchura de ficheros en el componente
			List<String> modulos = [ "" ]
			while (modulos.size() != 0) {
				String modulo = modulos.head()
				modulos.remove(modulo)
				// Intenta descargar el fichero
				File directorioModulo =	new File(dirComponente, modulo)
				directorioModulo.mkdirs();
				gitCommand = (gitCommand == null) || (gitCommand.trim().equals("")) ? "git" : gitCommand;
				String command = "${gitCommand} archive --remote=git@${gitHost}:${gitGroup}/${component}.git ${branch} ${modulo}${tecnologias[technology]} | tar xvf -";
				String commandTarget = "${gitCommand} archive --remote=git@${gitHost}:${gitGroup}/${component}.git ${branch} ${modulo}target | tar xvf -";
				CommandLineHelper buildCommandLineHelper = new CommandLineHelper(command);
				CommandLineHelper targetCommandLineHelper = new CommandLineHelper(commandTarget);
				buildCommandLineHelper.initLogger(this);
				def returnCode = null;
				try {
					// Se intenta bajar el pom.xml
					log("Intentando bajar el pom.xm...");
					returnCode = buildCommandLineHelper.execute(dirComponente);
					// Si ha ido bien la bajada del pom.xml se intenta bajar el posible directorio target asociado.
					log("Intentando bajar el target...");
					returnCode = targetCommandLineHelper.execute(dirComponente);
				} catch (Exception e) {
					logger.log("Error al ejecutar el comando ${command}. Código -> ${returnCode}");
				}

				// ¿Ha bajado?
				File buildFile = new File(directorioModulo.getCanonicalPath() + System.getProperty("file.separator") + tecnologias[technology] )
				if (buildFile.exists()) {
					List<String> mods = parseModules(modulo, buildFile, technology)
					modulos.addAll(mods)
				}
			}
		} else {
			//throw new Exception("Tecnología desconocida: ${technology}");
			log "Componente $component <-- Tecnología desconocida: ${technology}"
		}
		return dirComponente
	}

	/**
	 * Clase privada con la información de un artefacto.	 *
	 */
	private class ArtifactBeanLight {
		public String version
		public String groupId
		public String artifactId
		public boolean equals (object){
			if (object!=null){
				if (object.groupId==groupId && object.artifactId == this.artifactId)
					return true
			}
			return false
		}
	}
	
	/**
	 * Expone la API para crear un fichero de reactor en el directorio base
	 * elegido, ejecuta maven para validar el reactor y crea a partir del mismo
	 * el json de artefactos.
	 * @param components Listado de componentes leído de RTC
	 * @param baseDirectory Directorio base
	 */
	public List<MavenComponent> buildArtifactsFile(List components, File baseDirectory, List<String> finalComponentsList = null) {
		List<MavenComponent> ret = null;
		createParentReactor(components, baseDirectory)
		// Creación del artifacts.json
		Map<String, List<File>> poms = new HashMap<String, List<File>>();
		components.each { component->
			log ("Incluyendo componente: " + component)
			poms.put(component, getPoms(new File(baseDirectory, component)))
		}
		processSnapshotMaven(action, poms, parentWorkspace, baseDirectory, finalComponentsList)
		// Redistribuir las dependencias en los pom.xml a partir del fichero artifacts.json
		// Para ello es necesario convertir las dependencias de cada pom.xml en dependencias
		//	al artefacto de cabecera de cada componente.
		
		return buildDependencyGraph(components, baseDirectory)
	}


	/**
	 * Este método intenta desentrañar el árbol de dependencias simplificándolo
	 * para evitar que se intercalen artefactos de distintos módulos en el mismo
	 * componente
	 * Devuelve el grafo de dependencias ordenado
	 * @param components
	 * @param baseDirectory
	 * @return
	 */
	public List<MavenComponent> buildDependencyGraph(List components, File baseDirectory) {
		VersionUtils vUtils = new VersionUtils();
		Map<String, MavenComponent> headerArtifacts = [:]
		components.each { String component ->
			File headerPom = new File(baseDirectory, "$component/pom.xml");
			if (headerPom.exists()) {
				def headerPomXML = new XmlSlurper().parseText(headerPom.text);
				MavenCoordinates coords = 
					new MavenCoordinates(
						headerPomXML.groupId.text(), 
						headerPomXML.artifactId.text(), 
						vUtils.solve(
							headerPomXML, headerPomXML.version.text()));
				MavenComponent headerComponent = new MavenComponent(component);
				headerArtifacts.put(component, headerComponent);
				headerComponent.addArtifact(coords);
			}
		}
		log "headerArtifacts <- $headerArtifacts"
		Map<String, MavenComponent> artifacts = [:]
		new JsonSlurper().parseText(new File(parentWorkspace, "artifactsAll.json").text).each { def artifact ->
			MavenComponent comp = null;
			if (!artifacts.containsKey(artifact.component)) {
				comp = new MavenComponent(artifact.component);
				artifacts[artifact.component] = comp;
			}
			else {
				comp = artifacts[artifact.component];
			}
			comp.addArtifact(new MavenCoordinates(artifact.groupId, artifact.artifactId, artifact.version));
		}
		log "artifacts <- $artifacts"
		// Conociendo los artefactos de cabecera, recorrer todos los pom.xml
		//	y, si se identifica mediante el artifacts.json una dependencia interna
		//	de la construcción, sustituirla al vuelo
		baseDirectory.traverse(
			type: groovy.io.FileType.FILES,
			preDir: { if (it.name.startsWith(".") || it.name == 'target') return FileVisitResult.SKIP_SUBTREE},
			maxDepth: -1
			)
		{ File file ->
			if ("pom.xml".equals(file.getName())) {
				log "Revisando $file ..."
				boolean intact = true;
				def pom = new XmlParser().parseText(file.text);
				// El grupo y la versión pueden no estar explícitos, sino definidos en el parent
				def tmpGroup = null;
				def tmpVersion = null
				if (pom.version != null
						&& pom.version.text() != null
						&& pom.version.text().trim().length() > 0) {
					tmpVersion = pom.version.text()
				}
				else {
					tmpVersion = pom.parent.version.text()
				}
				if (pom.groupId != null 
						&& pom.groupId.text() != null
					    && pom.groupId.text().trim().length() > 0) {
					tmpGroup = pom.groupId.text();
				}
				else {
					tmpGroup = pom.parent.groupId.text();
				}						
				tmpVersion = vUtils.solveRecursive(baseDirectory, pom, tmpVersion) 
				// Buscar el componente al que corresponde este pom.xml
				MavenComponent artifactComponent = artifacts.values().find { MavenComponent it ->
					return (
						it.contains(
							new MavenCoordinates(
								tmpGroup, 
								pom.artifactId.text(), 
								vUtils.solve(pom, tmpVersion))))
				};
				pom.dependencies.dependency.each { dependency ->
					// Para cada dependencia, buscar si corresponde con un artefacto en este proceso
					MavenComponent dependencyComponent = artifacts.values().find { MavenComponent it ->
						return (
							it.contains(
								new MavenCoordinates(
									dependency.groupId.text(),
									dependency.artifactId.text(),
									vUtils.solveRecursive(
										baseDirectory, pom, dependency.version.text())
									)));
					}
					// En caso positivo:
					if (dependencyComponent != null) {
						// ¿Pertenecen al mismo componente?
						if (!artifactComponent.equals(
								dependencyComponent)) {
							log "---> $artifactComponent depende de $dependencyComponent"
							artifactComponent.addDependency(dependencyComponent)												
						}
					}
				};
			}
		}
		// Ordenar el grafo de dependencias
		List<MavenComponent> comps = []
		comps.addAll(artifacts.values());
		List<MavenComponent> ret = [];
		// Recorrer los componentes aplicando el algoritmo hasta obtener una
		//	lista ordenada, que los contenga todos
		comps.each { MavenComponent comp ->
			walkDependencyGraph(comp).each { MavenComponent it ->
				if (!ret.contains(it)) {
					ret << it;
				}
			}
		}		
		log "### Reactor order: " + ret.toString();
		return ret;
	}
	
	/**
	 * Recorrido de árbol de dependencias visto en
	 * http://www.electricmonk.nl/docs/dependency_resolving_algorithm/dependency_resolving_algorithm.html
	 * @param comp
	 * @return
	 */
	private List<MavenComponent> walkDependencyGraph(MavenComponent comp) {
		List<MavenComponent> ret = [];
		walkDependencyGraphI(comp, ret);
		return ret;
	}
	
	/**
	 * Inmersión recursiva del recorrido del grafo
	 * @param comp
	 * @param resolved
	 * @return
	 */
	private List<MavenComponent> walkDependencyGraphI(
			MavenComponent comp, List<MavenComponent> resolved) {
		comp.getDependencies().each { MavenComponent dep ->
			if (!resolved.contains(dep)) {
				walkDependencyGraphI(dep, resolved);
			}
		}
		resolved << comp;
	}
			
}
