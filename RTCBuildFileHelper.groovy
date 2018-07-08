package es.eci.utils
import java.nio.charset.Charset
import java.util.List;

import components.MavenComponent
import es.eci.utils.base.Loggable
import es.eci.utils.pom.MavenCoordinates
import es.eci.utils.pom.PomNode
import es.eci.utils.pom.PomTree
import groovy.io.FileVisitResult
import groovy.json.JsonSlurper
import groovy.xml.*
import es.eci.utils.VersionUtils;

/**
 * Esta clase recupera la estructura de buildfiles de un componente RTC
 */
class RTCBuildFileHelper extends Loggable {

	//-------------------------------------------------------------------
	// Constantes de la clase

	// Nombre del reactor eci
	private static final String REACTOR_FILE_NAME = "reactor_sort_jobs"


	//-------------------------------------------------------------------
	// Propiedades de la clase

	// Tecnologías soportadas por el parser
	private static Map tecnologias = ['maven': 'pom.xml']
	// Invocación a comando SCM ligero
	private boolean ligero = false;
	// Acción del proceso de IC (build, deploy, release, addFix, addHotfix)
	private String action;
	// Directorio base del proceso de IC (sobre este directorio se devuelven
	//	los resultados; sin embargo, todos los comandos RTC se
	//	se lanzan sobre directorio temporal aparte de éste
	private File parentWorkspace;
	// Utilidades de versions
	private VersionUtils vUtils = new VersionUtils();

	//-------------------------------------------------------------------
	// Métodos de la clase

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
						ret << buildFileName(currentModule, module.text())
					}
				}
			}
		}
		return ret
	}

	/**
	 * Constructor del helper
	 * @param ligero Indica si el helper debe utilizar el comando ligero de RTC
	 * @param log Closure para logging
	 */
	public RTCBuildFileHelper(String action, File parentWorkspace, boolean ligero, Closure log) {
		this.action = action
		this.parentWorkspace = parentWorkspace
		this.ligero = ligero
		if (log != null) {
			initLogger(log)
		}
	}

	/** 
	 * Constructor con las opciones por defecto: RTC pesado, sin logging
	 */
	public RTCBuildFileHelper(String action, File parentWorkspace) {
		this(action, parentWorkspace, true, null)
	}

	// Compone el nombre del fichero
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

	// Limpia restos de RTC del directorio
	private void cleanRTC(File directory) {
		if (directory != null && directory.exists() && directory.isDirectory()) {
			def remove = { dir ->
				File eliminar = new File(directory, dir)
				eliminar.deleteDir()
			}
			[ ".jazz5", ".metadata" ].each { dir -> remove(dir) }
		}
	}

	/**
	 * Crea el reactor de la corriente
	 * @param jobs Lista de componentes en la corriente
	 * @param home Directorio temporal donde se ha bajado los pom.xml
	 */
	private void createParentReactor(List<String> jobs, File home) {
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
		jobs.each { job ->
			if (new File("${home.canonicalPath}/${job}/pom.xml").exists()) {
				parentReactor << "\t\t\t<module>${job}</module>\n"
			}
		}
		parentReactor << "\t\t</modules>\n"
		parentReactor << "\t</project>\n"
	}

	/**
	 * Este método crea un repositorio de RTC y le añade 
	 * de la corriente los componentes
	 * @param stream Corriente RTC
	 * @param componentes Lista de nombres de componente
	 * @param userRTC Usuario RTC
	 * @param pwdRTC Password RTC
	 * @param urlRTC URL de repositorio RTC
	 * @param baseDir Directorio base de ejecución
	 * @return Nombre del workspace creado
	 */
	private String setupRepositoryWorkspace(
			String stream,
			List<String> componentes,
			String userRTC,
			String pwdRTC,
			String urlRTC,
			File baseDir) {
		ScmCommand command = new ScmCommand(ScmCommand.Commands.LSCM);
		command.initLogger(this);
		long timestamp = new java.util.Date().getTime();
		String ret = "WSR_${stream}_${timestamp}";
		command.ejecutarComando(
				"create workspace -e \"${ret}\"",
				userRTC,
				pwdRTC,
				urlRTC,
				baseDir);
		componentes.each { componente ->
			command.ejecutarComando(
					"workspace add-components \"${ret}\" \"${componente}\" -s \"${stream}\"",
					userRTC,
					pwdRTC,
					urlRTC,
					baseDir);
		}
		return ret;
	}

	/**
	 * Este método elimina un WSR
	 * @param workspaceRTC Workspace de RTC 
	 * @param userRTC Usuario RTC
	 * @param pwdRTC Password RTC
	 * @param urlRTC URL del repositorio RTC
	 * @param baseDir Directorio base
	 */
	private void deleteRepositoryWorkspace(
			String workspaceRTC,
			String userRTC,
			String pwdRTC,
			String urlRTC,
			File baseDir) {
		ScmCommand command = new ScmCommand(ScmCommand.Commands.LSCM);
		command.initLogger(this);
		command.ejecutarComando(
				"workspace delete \"${workspaceRTC}\" ",
				userRTC,
				pwdRTC,
				urlRTC,
				baseDir)
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
		artifact.version = vUtils.solveRecursive(baseDirectory, pom, artifact.version)
		// Resolver la versión contra una propiedad si fuera necesario
		artifact.artifactId = pom.artifactId.text()
		artifact.groupId = pom.groupId.text()
		if (artifact.groupId==null || artifact.groupId.length()==0) {
			artifact.groupId = pom.parent.groupId.text()
		}
		return artifact
	}

	// Devuelve la lista de artefactos maven en una serie de ficheros pom.xml
	def getArtifactsMaven(Map<String, List<File>> ficheros, File baseDirectory) {
		def artifacts = [:]
		ficheros.each { fComp ->
			def comp = fComp.key
			def f = fComp.value
			def list = []
			f.each { File fichero ->
				log ("Intentando leer ${fichero} ...")
				try {
					def pom = new XmlParser().parseText(fichero.text)
					list.add(getArtifactMaven(pom, baseDirectory))
				}
				catch(Exception e) {
					log fichero.getCanonicalPath()
					log fichero.size() + " bytes"
					log fichero.text
					throw e
				}
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
	public boolean processSnapshotMaven(String action, Map<String, List<File>> ficheros,
			File home, File baseDirectory, List<String> finalComponentsList = null) {
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

		VersionUtils vUtils = new VersionUtils();
		vUtils.writeJsonArtifactsMaven(artifactsComp, home, "artifacts.json", finalComponentsList);
		vUtils.writeJsonArtifactsMaven(artifactsComp, home, "artifactsAll.json", null);

		return true
	}

	// Escribe el fichero de artefactos maven en el directorio de la construcción
	def writeJsonArtifactsMaven(artifactsComp, File home, List<String> finalComponentsList = null){
		def artifactsJsonFile = new File("${home.canonicalPath}/artifacts.json");
		def artifactsJsonAllFile = new File("${home.canonicalPath}/artifactsAll.json");
		artifactsJsonFile.delete();
		artifactsJsonAllFile.delete();
		def cont1 = 0
		def cont4 = 0
		artifactsJsonFile << "["
		artifactsJsonAllFile << "["
		artifactsComp.each { artsComp ->			
			// Añadimos al artifactsJson sólo los componentes que entran en la release.
			if(finalComponentsList.contains(artsComp.key.trim())) {
				cont1 = cont1 + 1
				def cont2 = 0				
				def comp = artsComp.key
				def artifacts = artsComp.value
				artifacts.each{ artifact ->
					cont2 = cont2 +1
					artifactsJsonFile << "{\"version\":\"${artifact.version}\",\"component\":\"${comp}\",\"groupId\":\"${artifact.groupId}\",\"artifactId\":\"${artifact.artifactId}\"}"
					if (cont1 < finalComponentsList.size() || cont2 != artifacts.size() )
						artifactsJsonFile << ","
				}
			}

			def cont3 = 0			
			def comp = artsComp.key
			def artifacts = artsComp.value
			artifacts.each{ artifact ->
				cont3 = cont3 +1
				artifactsJsonAllFile << "{\"version\":\"${artifact.version}\",\"component\":\"${comp}\",\"groupId\":\"${artifact.groupId}\",\"artifactId\":\"${artifact.artifactId}\"}"
				if (cont1 < artifacts.size() || cont3 != artifacts.size() )
					artifactsJsonAllFile << ","
			}

		}
		artifactsJsonFile << "]";
		artifactsJsonAllFile << "]";
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
	 * @param baseDirectory Directorio base (temporal)
	 * @param stream Corriente RTC
	 * @param component Componente a bajar
	 * @param userRTC Usuario RTC
	 * @param pwdRTC Password de usuario RTC
	 * @param urlRTC URL del repositorio RTC
	 * @return lista de componentes ordenados
	 */
	public List<MavenComponent> createStreamReactor(
			File baseDirectory,
			String stream,
			String technology,
			String userRTC,
			String pwdRTC,
			String urlRTC,
			String componentesRelease = null,
			List<String> finalComponentsList = null
	) {
		String workspaceRTC = "";
		try {
			// Listado de componentes
			List<String> components;
			if(componentesRelease == null || componentesRelease.trim().equals("")) {
				components = new ComponentVersionHelper().getComponents(
						baseDirectory,
						stream,
						userRTC,
						pwdRTC,
						urlRTC);
			} else {
				components = componentesRelease.split(",");
			}
			// Setup del workspace de repositorio
			workspaceRTC =
					setupRepositoryWorkspace(
					stream,
					components,
					userRTC,
					pwdRTC,
					urlRTC,
					baseDirectory);
			// Descarga de los respectivos ficheros de construcción
			components.each { String component ->
				createBuildFileStructure(
						baseDirectory,
						workspaceRTC,
						component,
						technology,
						userRTC,
						pwdRTC,
						urlRTC);
			}

			// Construcción del reactor padre
			return buildArtifactsFile(components, baseDirectory, finalComponentsList)
		}
		finally {
			deleteRepositoryWorkspace(workspaceRTC, userRTC, pwdRTC, urlRTC, baseDirectory);
			ScmCommand stop = new ScmCommand(ScmCommand.Commands.LSCM);
			stop.initLogger(this);
			stop.detenerDemonio(baseDirectory);
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
			poms.put(component, getPoms(new File(baseDirectory, component)))
		}
		processSnapshotMaven(action, poms, parentWorkspace, baseDirectory, finalComponentsList);
		// Redistribuir las dependencias en los pom.xml a partir del fichero artifacts.json
		// Para ello es necesario convertir las dependencias de cada pom.xml en dependencias
		//	al artefacto de cabecera de cada componente.

		return buildDependencyGraph(components, baseDirectory)
	}

	/** 
	 *  Este método intenta desentrañar el árbol de dependencias simplificándolo
	 *	para evitar que se intercalen artefactos de distintos módulos en el mismo
	 *	componente
	 * @param components Lista de componentes de la corriente
	 * @param baseDirectory Directorio base donde ha bajado el código
	 * @return Lista ordenada de componentes para la construcción
	 */
	public List<MavenComponent> buildDependencyGraph(List components, File baseDirectory) {
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

	// Recorrido de árbol de dependencias visto en
	// http://www.electricmonk.nl/docs/dependency_resolving_algorithm/dependency_resolving_algorithm.html
	private List<MavenComponent> walkDependencyGraph(MavenComponent comp) {
		List<MavenComponent> ret = [];
		walkDependencyGraphI(comp, ret);
		return ret;
	}

	// Inmersión recursiva del recorrido del grafo
	private List<MavenComponent> walkDependencyGraphI(
			MavenComponent comp, List<MavenComponent> resolved) {
		comp.getDependencies().each { MavenComponent dep ->
			if (!resolved.contains(dep)) {
				walkDependencyGraphI(dep, resolved);
			}
		}
		resolved << comp;
	}

	// Devuelve el listado de pom.xml bajo un directorio, excluyendo aquellos que
	//	no debemos utilizar por no formar parte del árbol real de pom.xml
	def getPoms(File fromDir, String fileMatch = "pom\\.xml"){
		List<File> files = []
		PomTree tree = new PomTree(fromDir);
		for (Iterator<PomNode> iterator = tree.widthIterator(); iterator.hasNext();) {
			PomNode node = iterator.next();
			files << node.getFile()
		}
		return files
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
			String workspaceRTC,
			String component,
			String technology,
			String userRTC,
			String pwdRTC,
			String urlRTC) {
		File dirComponente = null
		if (tecnologias.keySet().contains(technology)) {
			dirComponente = new File(baseDirectory, component)
			dirComponente.mkdirs()
			// Búsqueda en anchura de ficheros en el componente
			List<String> modulos = [ "" ]
			ScmCommand comando = new ScmCommand(ScmCommand.Commands.LSCM)
			comando.initLogger(this)
			while (modulos.size() != 0) {
				String modulo = modulos.head()
				modulos.remove(modulo)
				// Intenta descargar el fichero
				String ficheroRTC = buildFileName(component, modulo, tecnologias[technology])
				String ficheroRTCTarget = buildFileName(component, modulo, "target")
				File directorioModulo =	new File(dirComponente, modulo)
				directorioModulo.mkdirs()
				log("Bajando ${modulo} para crear el árbol de poms...");
				comando.ejecutarComando("load \"${workspaceRTC}\" -t \"${directorioModulo.canonicalPath}\" \"${ficheroRTC}\" --force",
						userRTC, pwdRTC, urlRTC, baseDirectory);
				comando.ejecutarComando("load \"${workspaceRTC}\" -t \"${directorioModulo.canonicalPath}\" \"${ficheroRTCTarget}\" --force",
						userRTC, pwdRTC, urlRTC, baseDirectory);
				// ¿Ha bajado?
				File buildFile = new File(directorioModulo, tecnologias[technology] )
				if (buildFile.exists()) {
					List<String> mods = parseModules(modulo, buildFile, technology)
					modulos.addAll(mods)
				}
			}
		}
		return dirComponente
	}

	// Clase privada con la información de un artefacto
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
}
