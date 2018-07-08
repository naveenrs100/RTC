package es.eci.utils

import java.io.File;

import es.eci.utils.pom.MavenCoordinates
import es.eci.utils.pom.PomNode
import es.eci.utils.pom.PomTree
import groovy.io.FileVisitResult
import groovy.json.*
import groovy.xml.*
import hudson.model.*

/**
 * Agrupa funcionalidad de lectura de versión, actualización, etc.
 */
class VersionUtils {

	private List<String> exceptionsList;

	private static final Map tecnologias = ["maven":"pom\\.xml","gradle":"build\\.gradle"]

	private boolean fixMavenErrors = false

	// Cache de propiedades
	private Map<File, PomTree> propertiesCache = [:]

	//---------------> Funciones

	/**
	 * Construye un versionador con la excepción del directorio target
	 */
	public VersionUtils() {
		setup("target")
	}

	/**
	 * Construye un objeto de versionado con las excepciones indicadas.
	 * @param strExceptions Cadena que determina las excepciones, que son los
	 * directorios en los que no va a entrar a buscar ficheros de construcción.
	 */
	public VersionUtils(String strExceptions) {
		setup(strExceptions)
	}

	// Setup del versionado
	private void setup(String strExceptions) {
		exceptionsList = new LinkedList<String>();
		if (strExceptions != null && strExceptions.trim().length() > 0) {
			for(String exception: strExceptions.split(",")) {
				exceptionsList.add(exception)
			}
		}
		if (!exceptionsList.contains("target")) {
			exceptionsList.add("target")
		}
	}

	//------------ GRADLE -----------

	def gradleGetVersion(fichero){
		def version = new Version()

		fichero.eachLine { line ->
			def mVersion = line =~ /.*version\s?=\s?["|'](.*)["|']/
			def mGroupId = line =~ /.*group\s?=\s?["|'](.*)["|']/
			if (mGroupId.matches()) version.groupId = mGroupId[0][1]
			if (mVersion.matches()) version.version = mVersion[0][1]
		}

		return version
	}

	def gradleWriteVersion(fichero, version, newVersion,parentWorkspace){
		def fileText = fichero.text
		fileText = fileText.replaceAll("${version}","${newVersion}")
		fichero.write(fileText);
		def changed = this.getChangedfile(parentWorkspace)
		changed << "${fichero}\n"
	}

	//------------- MAVEN -----------------

	def mavenVersionCheck(ficheros,version){
		ficheros.each { fichero ->
			try {
				def pom = new XmlParser().parse(fichero)
				def testVersion = pom.packaging.text()=="pom"?pom.version.text():pom.parent.version.text()
				if (testVersion!=version.version){
					log.log "WARNING: The parent version in ${fichero} is not the parent version of this component: ${version.groupId} - ${version.version}\n"
					fixMavenErrors = true
				}
			}
			catch(Exception e) {
				log.log("AVISO: el fichero $fichero no es parseable -> " + e.getMessage());
				throw e;
			}
		}
	}


	def getArtifactsStream(homeStream){
		def artifactsFile = new File("${homeStream}/artifacts.json")
		def artifacts = []
		if (artifactsFile.exists()){
			def text = new StringBuffer()
			artifactsFile.eachLine { line -> text << line}
			artifacts = new JsonSlurper().parseText(text.toString())
		}
		return artifacts
	}

	def getArtifacts(homeStream,ficheros,home){
		def artifacts = getArtifactsStream(homeStream)
		if (artifacts.size()==0) artifacts = getArtifactsComponent(ficheros,home)
		return artifacts
	}

	def getArtifactsComponent(ficheros,home){
		MavenUtils.processSnapshotMaven(ficheros,home)
		return getArtifactsStream(home)
	}

	/**
	 * Comprueba que no hay ninguna versión snapshot
	 */
	def mavenSnapshotCheck(fichero,err,artifacts){
		def pom = new XmlParser().parse(fichero)
		pom.dependencies.dependency.each { dependency ->
			def version = dependency.version.text()
			if (version!=null){
				if (version.toLowerCase().indexOf("snapshot")>0){
					if (artifacts.find {a -> a.artifactId == dependency.artifactId.text() && a.groupId == dependency.groupId.text()}==null){
						//err << "There is a snapshot version in ${dependency.artifactId.text()} inside ${fichero}\n"
						err << "ERROR: la dependencia a ${dependency.groupId.text()}:${dependency.artifactId.text()}:${version} dentro de ${fichero} no puede contener -SNAPSHOT\n"
					}
				}
			}
		}
		return err
	}

	/**
	 * Este método determina la profundidad de un directorio
	 */
	def getDepth(file) {
		File f = null
		if (file instanceof java.io.File) {
			f = file
		}
		else if (file instanceof String) {
			f = new File(file)
		}
		int ret = -1;
		if (f.isDirectory()) {
			// Si es un directorio, devolver su profundidad
			return f.getAbsolutePath().split("\\" + File.separator).size()
		}
		else {
			// Si es un fichero, la profundidad del directorio que lo contiene
			return f.getParent().split("\\" + File.separator).size()
		}
	}

	/**
	 * Este método recupera la versión del pom.xml que hemos determinado es el
	 * padre de todo el proyecto.  Se determina que el pom padre del proyecto es
	 * aquel que encontremos a la altura del componente, o el que se encuentre como
	 * máximo una altura por debajo.  Si el primer nivel estuviera vacío y hubiera
	 * varios candidatos en el segundo nivel, se devuelve un error.
	 * @param ficheros Listado de ficheros maven obtenidos desde el origen
	 * @param parentWorkspace Ubicación del workspace del job invocante, donde
	 *	asumimos que encontraremos los fuentes
	 * @param checkSnapshot Si es cierto, se procede a comprobar que las
	 *	dependencias no apunten a ningún proyecto en desarrollo (con -SNAPSHOT)
	 * @param checkErrors Si vale 'true', el método es tolerante a errores
	 *	de formato o bien a que exista más de un fichero de construcción de
	 *	primer nivel
	 * @param homeStream Corriente RTC que aloja el código
	 * @param fullCheck Booleano que indica que la comprobación de los artefactos debe hacerse sobre el
	 * 	total de componentes desplegados en el homestream
	 */
	def mavenGetVersion(parentWorkspace, ficheros, checkSnapshot, checkErrors, homeStream, Boolean fullCheck){
		def version = null
		def err = new StringBuffer()
		def n = 0
		def l = -1
		def baseDepth = getDepth(parentWorkspace)

		def versionCandidate = null

		def artifacts = null
		if (checkSnapshot!="false") {
			if (fullCheck == null || fullCheck == Boolean.FALSE) {
				artifacts = getArtifacts(homeStream,ficheros,parentWorkspace)
			}
			else {
				artifacts = getArtifacts(homeStream, this.getAllFiles(homeStream, tecnologias.get("maven")) ,parentWorkspace)
			}
		}

		ficheros.each { fichero ->
			try {
				def pom = new XmlParser().parse(fichero)
				if (checkSnapshot!="false") {
					err = this.mavenSnapshotCheck(fichero,err,artifacts)
				}
				def depth = getDepth(fichero)

				if (depth == baseDepth) {
					// Me quedo con éste
					version = new Version();
					version.version = this.solve(pom, pom.version.text())
					version.groupId = pom.groupId.text()
					log.log "Version from: ${fichero} -> ${version.version}"
				}
				else if (depth == baseDepth + 1) {
					// Considerar el candidato
					versionCandidate = new Version()
					versionCandidate.version = this.solve(pom, pom.version.text())
					versionCandidate.groupId = pom.groupId.text()
					log.log "Version candidate from: ${fichero} -> ${versionCandidate.version}"
					n++
				}
			}
			catch(Exception e) {
				log.log "Error parseando ${fichero} -> ${e.getMessage()}"
			}
		}

		if (version == null && versionCandidate == null) {
			err << "There is no root pom.xml in this component!!"
		}
		if (version == null && versionCandidate != null && n>1) {
			err << "There are more than one root pom.xml in this component!!!"
		}
		if (err.length()>0 && checkErrors!="false") {
			throw new NumberFormatException(err.toString())
		}
		// Si no hay versión en el primer nivel y sí tenemos un candidato en el
		//	segundo, tomar la versión
		if (version == null && versionCandidate != null && n == 1) {
			version = versionCandidate
		}
		this.mavenVersionCheck(ficheros,version)
		log.log "Versión -> " + version.version
		log.log "Grupo -> " + version.groupId
		return version
	}

	/**
	 * Recorre el pom buscando las dependencias del módulo.  Si la dependencia
	 * alude a un artefacto que forma parte del propio proyecto, y se refiere al
	 * mismo en una versión abierta, la reemplaza por una versión cerrada de la misma.
	 * @param pom Fichero pom.xml expresado como XML parseado por groovy
	 * @param artifacts Lista de artefactos deducida de leer el conjunto de ficheros pom.xml
	 * @param changeVersion Indica si el método actualiza el formato de versión
	 *	antiguo (3 dígitos) al nuevo (4 + hotFix opcional).  Vale 'true' o bien 'false'
	 */
	def removeSnapshotDependencies(pom,artifacts,changeVersion){
		pom.dependencies.dependency.each { dependency ->
			def version = dependency.version.text()
			if (version!=null){
				if (version.toLowerCase().indexOf("snapshot")>0){
					if (artifacts.find {a -> a.artifactId == dependency.artifactId.text() && a.groupId == dependency.groupId.text()}!=null){
						dependency.version[0].setValue(this.getNewVersion(version,"removeSnapshot",changeVersion))
					}
				}
			}
		}
		return pom
	}

	/**
	 * Indica si la versión está expresada como un valor explícito (true) o
	 * bien si está indicada como referencia a una variable (false).
	 * P. ej.:
	 * <version>2.0.49.0-SNAPSHOT</version> -> devuelve true
	 * <version>${tpvVersion}</version> 	-> devuelve false
	 * @param pom Objeto resultante de parsear un pom.xml
	 */
	def isExplicitVersion(pom) {
		boolean ret = false;
		if (pom != null) {
			if (pom.version != null && pom.version.text().trim().length() > 0) {
				ret = !pom.version.text().contains('${');
				log.log("Versión explícita: $ret");
			}
			else if (pom.parent.version != null && pom.parent.version.text().trim().length() > 0) {
				ret = !pom.parent.version.text().contains('${');
			}
		}
		return ret;
	}

	/**
	 * Este método debe asegurarse de que, en el caso de que la versión no sea
	 *	explícita (sino que esté expresada como referencia a una variable), dicha
	 *	versión esté expresada como referencia a una sola variable.  De esta forma:
	 *	<version>${miVariable}</version>	->	Válida (el método no hace nada)
	 *	<version>${miVariable}-${otraVariable}-${otraVariableMás}</version>
	 *		-> Salta una excepción
	 */
	def validateNotExplicitVersion(pom) {
		String version = null;
		if (pom.version[0] != null) {
			version = pom.version[0].text();
		}
		else if (pom.parent.version[0] != null) {
			version = pom.parent.version[0].text();
		}
		if (version != null) {
			// Validar la versión
			List componentes = parse(version);
			if (componentes.size() > 1) {
				throw new Exception("La versión ($version), si no es explícita, debe estar expresada en función de una sola variable");
			}
		}
	}

	/**
	 * Hace las operaciones necesarias para, según la fase de construcción, quitar los
	 * 	-SNAPSHOT, incrementar el número de versión correspondiente y/o volver a poner
	 * 	los -SNAPSHOT de la versión en los ficheros de construcción.  La versión se define
	 *	con el formato 1.2.3.4(-5)(-SNAPSHOT), siendo:
	 *	1 y 2: definidos por el usuario
	 *	3: release
	 *	4: fix (antiguo release2release)
	 *	5: (opcional) hotfix
	 *	-SNAPSHOT: se mantiene mientras la versión se encuentre en desarrollo, se retira
	 *		al realizar una release
	 * Por ejemplo: 21.0.0.0-SNAPSHOT, al hacer una release se sube a RTC los pom.xml con
	 *	versión 21.0.0.0, y acto seguido se vuelven a modificar para dejar en desarrollo
	 *	la siguiente versión: 21.0.1.0-SNAPSHOT.  Este método opera sobre ficheros pom.xml
	 * (tecnología maven solamente)
	 * @param ficheros Lista de ficheros de construcción de la aplicación (en este caso,
	 * 	ficheros pom.xml solamente)
	 * @param version Versión original declarada en el pom raíz
	 * @param newVersion Versión calculada como nueva versión dependiendo de la acción que se
	 * 	esté llevando a cabo
	 * @param action Distingue las posibles acciones a llevar a cabo por el método.
	 *	addFix: Sería el equivalente al release2release, cambia el dígito 4
	 *	addHotfix: Mantenimiento correctivo de emergencia, cambia el dígito 5 (o lo pone
	 *		si la versión no lo tenía asignado)
	 *	removeSnapshot: Paso 1 del procedimiento de release.  Retira el -SNAPSHOT de las versiones
	 *		de los ficheros de construcción
	 *	addSnapshot: Paso 2 del procedimiento de release.  Una vez hecha y etiquetada en RTC la release,
	 *		incrementa el dígito 3 y pone el -SNAPSHOT para dejar la aplicación preparada para el
	 *		desarrollo de su próxima release.
	 * @param parentWorkspace Ubicación del workspace del job padre del que nos ha invocado,
	 *	donde el código groovy asume que va a encontrar los fuentes
	 * @param homeStream Corriente RTC que aloja el código
	 * @param changeVersion Indica si el método actualiza el formato de versión
	 *	antiguo (3 dígitos) al nuevo (4 + hotFix opcional).  Vale 'true' o bien 'false'
	 * @param fullCheck Booleano que indica que la comprobación de los artefactos debe hacerse sobre el
	 * 	total de componentes desplegados en el homestream
	 */
	def mavenWriteVersion(ficheros, version, newVersion, action, homeStream,parentWorkspace,changeVersion, Boolean fullCheck){
		def changed = this.getChangedfile(parentWorkspace)
		def ficheroRoot = this.getRootFile(parentWorkspace,tecnologias["maven"])
		log.log "ficheroRoot: ${ficheroRoot}"
		if (ficheroRoot==null || !ficheroRoot.exists())
			throw new Exception("No se encuentra pom raiz en ${parentWorkspace} con patron ${tecnologias['maven']}")

		def artifacts = null
		if (action=="removeSnapshot" || action=="addSnapshot" || action == 'addFix') {
			if (fullCheck == null || fullCheck == Boolean.FALSE) {
				artifacts = getArtifacts(homeStream,ficheros,parentWorkspace)
			}
			else {
				artifacts = getArtifacts(homeStream,this.getAllFiles(homeStream, tecnologias.get("maven")),parentWorkspace)
			}
		}

		// El pom raíz define una versión.  Si está definida como una propiedad, se guarda
		//	en esta variable
		def mainImplicitVersion = null
		ficheros.each { fichero ->
			def hayCambios = false
			try {
				def pom = new XmlParser().parse(fichero)
				// Se debe determinar si la versión está explícita o bien
				//	hace referencia a una propiedad definida en el padre.
				//	En tal caso, solo se actualiza la propiedad, y en caso de
				//	que sea sustituible de forma sencilla, es decir:
				//  <version>${tpvVersion}</version>
				// 	...
				// 	<properties><tpvVersion>21.0.0.0</tpvVersion></properties>
				//  Sería fácilmente sustituible: en el pom.xml donde está tpvVersion,
				//	se reemplaza la propiedad, y en todos los demás no se hace nada.
				//	Sin embargo, algo así:
				//	<version>${tpvVersion}-${calificador}</version>
				//	Daría una excepción al no tener una propiedad que se pueda actualizar
				//	fácilmente (por mucho que el build funcionase en este caso)
				boolean explicita = isExplicitVersion(pom);
				if (!explicita) {
					validateNotExplicitVersion(pom);
				}

				if (action=="removeSnapshot") {
					pom = this.removeSnapshotDependencies(pom, artifacts, changeVersion)
				}

				if (ficheroRoot.getCanonicalPath()==fichero.getCanonicalPath()){
					if (pom.version != null && pom.version.size() > 0 && pom.version[0]!=null) {
						if (explicita){
							pom.version[0].setValue(newVersion)
							hayCambios = true
							log.log("Write versión raíz explícita $newVersion en $fichero")
						}else{
							String laVersion = pom.version[0].text().substring(2, pom.version[0].text().length() - 1);
							mainImplicitVersion = laVersion
							if (lookupProperty(pom, laVersion) != null) {
								// Si el pom contiene la propiedad, sustituirla
								setProperty(pom, laVersion, newVersion);
								hayCambios = true
								log.log("Write versión raíz implícita $newVersion en la propiedad $mainImplicitVersion en $fichero")
							}
						}
					}else{
						throw new Exception("El pom raiz no tiene tag version");
					}
				}else{
					if (pom.version != null && pom.version.size() > 0 && pom.version[0]!=null) {
						pom.remove(pom.version[0])
						hayCambios = true
					}
					if (explicita) {
						pom.parent.version[0].setValue(newVersion)
						hayCambios = true
						log.log "Write ${newVersion} in ${fichero}"
					}
				}
				if (hayCambios) {
					XmlUtil.serialize(pom, new FileWriter(fichero))
					changed << "${fichero}\n"
				}
			}
			catch(Exception e) {
				log.log("AVISO: el fichero $fichero no es parseable -> " + e.getMessage())
			}
		}

		if (action=="removeSnapshot" || action=="addSnapshot" || action=="addFix") {
			// El fichero pom raíz puede tener dependencias a otros módulos de la
			//	aplicación definidas como propiedades.  En este caso, se debe distinguir
			//	qué propiedades del pom raíz son dependencias a otros módulos y
			//	aplicarles la acción necesaria (sea quitarle el snapshot o volver a ponérselo
			//	subiendo el tercer dígito)
			def versionesModulos = getImplicitMavenModuleDependencies(ficheros, artifacts)
			if (versionesModulos != null && versionesModulos.size() > 0) {
				log.log("Versiones de los módulos definidas como propiedad:")
				versionesModulos.each { propiedad -> log.log(propiedad + (mainImplicitVersion == propiedad?" (PRINCIPAL)":" (DEPENDENCIA)")) }
				def pomRaiz = new XmlParser().parse(ficheroRoot)
				versionesModulos.each { propiedad ->
					if ('parent.version' != propiedad && 'project.version' != propiedad && mainImplicitVersion != propiedad) {
						def versionOriginal = lookupProperty(pomRaiz, propiedad)
						if (versionOriginal.endsWith("-SNAPSHOT")) {
							def nuevaVersion = getNewVersion(versionOriginal, action, changeVersion)
							log.log("Actualizando la propiedad $propiedad al valor $nuevaVersion")
							setProperty(pomRaiz, propiedad, nuevaVersion)
							XmlUtil.serialize(pomRaiz, new FileWriter(ficheroRoot))
						}
					}
				}
			}
		}
	}

	/**
	 * Este método recorre la lista de ficheros pom.xml buscando aquellas dependencias
	 * que estén definidas de forma implícita, si corresponden a módulos dentro del mismo
	 * proyecto.
	 * @param ficheros Lista de ficheros pom.xml que compone la aplicación
	 * @param artifacts Lista de artefactos incluídos en la aplicación
	 */
	def List<String> getImplicitMavenModuleDependencies(ficheros, artifacts) {
		def ret = []
		ficheros.each { fichero ->
			try {
				def pom = new XmlParser().parse(fichero)
				pom.dependencies.dependency.each { dependency ->
					// ¿Es un artefacto de la aplicación?
					if (artifacts.find {a -> a.artifactId == dependency.artifactId.text() && a.groupId == dependency.groupId.text()}!=null){
						def version = dependency.version.text()
						if (version.startsWith('${') && version.endsWith('}')) {
							def propiedad = version.substring(2, version.length() - 1)
							if (!propiedad.startsWith("project.")) {
								// Apuntamos la propiedad para cerrarla e incrementarla luego
								if (!ret.contains(propiedad)) {
									ret << propiedad
								}
							}
						}
					}
				}
			}
			catch(Exception e) {
				log.log("AVISO: el fichero $fichero no es parseable -> " + e.getMessage())
			}
		}
		return ret;
	}

	//------------- SELECTOR ------------------

	/**
	 * Hace las operaciones necesarias para, según la fase de construcción, quitar los
	 * 	-SNAPSHOT, incrementar el número de versión correspondiente y/o volver a poner
	 * 	los -SNAPSHOT de la versión en los ficheros de construcción.  La versión se define
	 *	con el formato 1.2.3.4(-5)(-SNAPSHOT), siendo:
	 *	1 y 2: definidos por el usuario
	 *	3: release
	 *	4: fix (antiguo release2release)
	 *	5: (opcional) hotfix
	 *	-SNAPSHOT: se mantiene mientras la versión se encuentre en desarrollo, se retira
	 *		al realizar una release
	 * Por ejemplo: 21.0.0.0-SNAPSHOT, al hacer una release se sube a RTC los pom.xml con
	 *	versión 21.0.0.0, y acto seguido se vuelven a modificar para dejar en desarrollo
	 *	la siguiente versión: 21.0.1.0-SNAPSHOT.  Este método distingue las distintas tecnologías
	 * (gradle, maven, etc.) disponibles en el workflow.
	 * @param technology Nombre de la herramienta de construcción utilizada.  Dependiendo
	 *	de este nombre, se gestiona uno u otro tipo de fichero de construcción (pom.xml,
	 *	build.gradle, etc.)
	 * @param action Distingue las posibles acciones a llevar a cabo por el método.
	 *	addFix: Sería el equivalente al release2release, cambia el dígito 4
	 *	addHotfix: Mantenimiento correctivo de emergencia, cambia el dígito 5 (o lo pone
	 *		si la versión no lo tenía asignado)
	 *	removeSnapshot: Paso 1 del procedimiento de release.  Retira el -SNAPSHOT de las versiones
	 *		de los ficheros de construcción
	 *	addSnapshot: Paso 2 del procedimiento de release.  Una vez hecha y etiquetada en RTC la release,
	 *		incrementa el dígito 3 y pone el -SNAPSHOT para dejar la aplicación preparada para el
	 *		desarrollo de su próxima release.
	 * @param parentWorkspace Ubicación del workspace del job padre del que nos ha invocado,
	 *	donde el código groovy asume que va a encontrar los fuentes
	 * @param checkErrors Si vale 'true', el método es tolerante a errores
	 *	de formato o bien a que exista más de un fichero de construcción de
	 *	primer nivel
	 * @param homeStream Corriente RTC que aloja el código
	 * @param changeVersion Indica si el método actualiza el formato de versión
	 *	antiguo (3 dígitos) al nuevo (4 + hotFix opcional).  Vale 'true' o bien 'false'
	 * @param fullCheck Booleano que indica que la comprobación de los artefactos debe hacerse sobre el
	 * 	total de componentes desplegados en el homestream
	 */
	def changeFileVersion (technology,action,parentWorkspace,save,checkSnapshot,checkErrors,homeStream,changeVersion, Boolean fullCheck) {

		def fileVersion = null
		def version = null
		def newVersion = null

		if (technology=="gradle"){
			// Búsqueda del fichero de contrucción gradle sin límite de profundidad
			def fichero = this.getRootFile(parentWorkspace, tecnologias.get("gradle"))
			version = this.gradleGetVersion(fichero)
			log.log("Versión -> ${version.version}")
			newVersion = this.getNewVersion(version.version,action,changeVersion)
			log.log("Nueva versión -> ${newVersion}")
			if (version.version!=newVersion){
				this.gradleWriteVersion(fichero, version.version,newVersion,parentWorkspace)
			}
		}else if (technology=="maven"){
			def ficheros = this.getAllFiles(parentWorkspace, tecnologias.get("maven"))
			version = this.mavenGetVersion(parentWorkspace, ficheros, checkSnapshot,checkErrors, homeStream, fullCheck)
			checkSnapshotVersion(action, version)
			log.log("Versión -> ${version.version}")
			newVersion = this.getNewVersion(version.version,action,changeVersion)
			log.log("Nueva versión -> ${newVersion}")
			if (version.version!=newVersion || fixMavenErrors){
				this.mavenWriteVersion(ficheros, version.version,newVersion,action,homeStream,parentWorkspace,changeVersion, fullCheck)
			}

			// Bloque WAS deploy plugin
			if (action=="deploy") {
				ficheros.each { fichero ->
					this.addWASPluginVersion(fichero)
				}
			}
		}else{
			throw new NumberFormatException("Technology ${technology} is not supported")
		}


		if (save!="false"){
			if (save=="old") {
				this.writeStandarVersion(version.version,version.groupId,parentWorkspace,checkSnapshot)
			}
			else {
				this.writeStandarVersion(newVersion,version.groupId,parentWorkspace,checkSnapshot)
			}
		}
	}

	/**
	 * Comprueba que la versión esté abierta o cerrada en función de la acción solicitada
	 * @param action  Distingue las posibles acciones a llevar a cabo por el método.
	 *	addFix: Sería el equivalente al release2release, cambia el dígito 4
	 *	addHotfix: Mantenimiento correctivo de emergencia, cambia el dígito 5 (o lo pone
	 *		si la versión no lo tenía asignado)
	 *	removeSnapshot: Paso 1 del procedimiento de release.  Retira el -SNAPSHOT de las versiones
	 *		de los ficheros de construcción
	 *	addSnapshot: Paso 2 del procedimiento de release.  Una vez hecha y etiquetada en RTC la release,
	 *		incrementa el dígito 3 y pone el -SNAPSHOT para dejar la aplicación preparada para el
	 *		desarrollo de su próxima release.
	 */
	def checkSnapshotVersion(String action, Version version) {
		if (action=="build" || action=="deploy" || action=="removeSnapshot") {
			if (!version.version.endsWith("-SNAPSHOT")) {
				throw new NumberFormatException("La versión del entregable (${version.version}) no es válida; debe tener el siguiente formato: X.X.X.X-SNAPSHOT")
			}
		}
	}

	/**
	 * Realiza la adicción de la propiedad "plugin_was_version" al pom.xml
	 * del módulo ear de los proyectos que lo tengan.
	 *
	 * @param fichero Fichero que se va a parsear
	 */
	def addWASPluginVersion(File fichero) {
		try {
			def pom = new XmlParser().parse(fichero)

			if (pom.packaging.text() == 'ear') {

				log.log('Update WAS plugin property in ear project...')

				def hasProperties = false
				def hasPluginWASVersion = false
				def changes = false

				if (pom.properties != null && pom.properties.size() > 0) {
					hasProperties = true
					pom.properties['*'].each { propertiesChild ->
						String nombreNodo = propertiesChild.name().getLocalPart()
						log.log nombreNodo
						if ('plugin_was_version' == nombreNodo) {
							log.log "--> plugin_was_version found"
							hasPluginWASVersion = true
						}
					}
				}

				log.log "hasProperties $hasProperties"
				log.log "hasPluginWASVersion $hasPluginWASVersion"

				if (!hasProperties) {
					pom.appendNode('properties')
					changes = true
				}

				if (!hasPluginWASVersion) {
					pom.properties[0].appendNode('plugin_was_version', '1.0.5.0')
					changes = true
				}

				// Localizar el plugin
				if (pom.profiles?.size() > 0) {
					pom.profiles.profile.each { profile ->
						// Para cada profile
						if (profile.id?.text() == 'DEPLOYER') {
							// Si estoy en el deployer
							profile.build?.plugins?.plugin.each { plugin ->
								if (plugin.artifactId?.text() == 'maven-deploy-was-ear-plugin' && plugin.groupId?.text() == 'com.atsistemas.maven') {
									plugin.groupId[0].setValue("es.eci.gis.maven")
									plugin.version[0].setValue("\${plugin_was_version}")
									changes = true
								}
							}
						}
					}
				}

				log.log "property was plugin value:${pom.properties.plugin_was_version.text()}"
				log.log "changes $changes"

				if (changes) XmlUtil.serialize(pom, new FileOutputStream(fichero))

				log.log('Finish WAS plugin property update')

			}
		} catch (FileNotFoundException e) {
			log.log e.getMessage()
		}
	}

	//---- COMUNES -----------


	def getRootFile(fromDirName,fileMatch){
		def fromDir = new File(fromDirName)
		def file = null
		// traverse recorre los directorios en orden aleatorio en la versión
		//	de groovy en la que se probó este código.  Se intenta asegurar
		//	mediante el código a continuación que se elige el fichero de menor
		//	profundidad
		fromDir.traverse(
				type: groovy.io.FileType.FILES,
				preDir: { if (it.name.startsWith(".") || it.name == 'target') return FileVisitResult.SKIP_SUBTREE},
				nameFilter: ~/${fileMatch}/,
				maxDepth: 2
				){
					def itDepth = getDepth(it)
					if (file == null || itDepth < getDepth(file)) {
						file = it
					}
				}
		log.log "Encuentra: ${file}....."
		return file

	}

	/**
	 * Búsqueda de ficheros con límite de profundidad
	 * @param fromDirName Directorio de inicio de la búsqueda
	 * @param fileMatch Patrón de búsqueda
	 * @param maxDepth Límite de profundidad.
	 * Si es -1: recursividad, sin límite
	 * Si es 0: no hace recursividad
	 * Si es mayor que cero: límite de la recursividad
	 */
	def getAllFiles(fromDirName,fileMatch, max){
		def fromDir = new File(fromDirName)
		def files = []
		fromDir.traverse(
				type: groovy.io.FileType.FILES,
				preDir: { if (exceptionsList.contains(it.name)
						|| it.name.startsWith(".")) return FileVisitResult.SKIP_SUBTREE},
				nameFilter: ~/${fileMatch}/,
				maxDepth: max
				){
					files << it
				}
		return files
	}

	/**
	 * Búsqueda de ficheros sin límite de profundidad
	 * @param fromDirName Directorio de inicio de la búsqueda
	 * @param fileMatch Patrón de búsqueda
	 */
	def getAllFiles(fromDirName,fileMatch){
		return getAllFiles(fromDirName, fileMatch, -1);
	}

	def addVersion(version,posicion){
		def tmpVersion = version.split("-")
		def aVersion = tmpVersion[0].split("\\.")
		def z = aVersion.length-posicion
		def tmp = new StringBuffer()
		aVersion.eachWithIndex() { obj, i ->
			if (i==z) {
				try{
					tmp << "${obj.toInteger()+1}"
				}catch(NumberFormatException e){
					throw new NumberFormatException("Formato de versión (${version}) no permite addVersion")
				}
			}else if (i>z){
				tmp << "0"
			}else{
				tmp << "${obj}"
			}
			if (i!=(aVersion.length-1))
				tmp << "."
		}
		return tmp
	}

	/**
	 * Chequea que el número de digitos de la versión es el adecuado y si no lo es lo arregla automáticamente.
	 */
	def setDigits(version, digits){
		def versionRes = ""
		def tmpVersion = version.split("-")
		def aVersion = tmpVersion[0].split("\\.")
		def size = aVersion.size()
		def sizeTmp = tmpVersion.size()
		--digits
		for (i in 0..digits){
			versionRes += i<size?aVersion[i]:"0"
			versionRes += i<digits?".":""
		}

		if (sizeTmp>1){
			for (i in 1..sizeTmp-1){
				versionRes += "-${tmpVersion[i]}"
			}
		}
		return versionRes
	}

	/**
	 * Cambia la versión dependiento de la acción que se necesite.
	 * Lanza NumberFormatException con el mensaje descriptivo del error
	 * @param version Número de versión
	 * @param action Distingue las posibles acciones a llevar a cabo por el método.
	 *	addFix: Sería el equivalente al release2release, cambia el dígito 4
	 *	addHotfix: Mantenimiento correctivo de emergencia, cambia el dígito 5 (o lo pone
	 *		si la versión no lo tenía asignado)
	 *	removeSnapshot: Paso 1 del procedimiento de release.  Retira el -SNAPSHOT de las versiones
	 *		de los ficheros de construcción
	 *	addSnapshot: Paso 2 del procedimiento de release.  Una vez hecha y etiquetada en RTC la release,
	 *		incrementa el dígito 3 y pone el -SNAPSHOT para dejar la aplicación preparada para el
	 *		desarrollo de su próxima release.
	 * @param changeVersion Indica si el método actualiza el formato de versión
	 *	antiguo (3 dígitos) al nuevo (4 + hotFix opcional)
	 */
	def getNewVersion(version,action,changeVersion){
		if (changeVersion!="false")
			version = setDigits(version,4)
		def newVersion = version
		if (action=="addFix"){
			def tmp = addVersion(version,1)
			newVersion = tmp.toString()
		}else if (action=="addHotfix"){
			def aVersion = version.split("-")
			def hotfix=1
			if (aVersion.length>1){
				try{
					hotfix=aVersion[1].toInteger()+1
				}catch(NumberFormatException e){
					throw new NumberFormatException("Formato de versión (${version}) no permite addHotfix")
				}
			}
			newVersion="${aVersion[0]}-${hotfix}"
		}else if (action=="removeSnapshot"){
			def aVersion = version.split("-")
			if ( aVersion.length!=2 || aVersion[1]!="SNAPSHOT") throw new NumberFormatException("Formato de versión (${version}) no permite removeSnapshot")
			newVersion=aVersion[0]
		}else if (action=="addSnapshot"){
			def tmp = addVersion(version,2)
			tmp << "-SNAPSHOT"
			newVersion = tmp.toString()
		}
		return newVersion
	}

	def writeStandarVersion(version,groupId,parentWorkspace,checkSnapshot){
		if (checkSnapshot=="false"){
			if (version.toLowerCase().indexOf("snapshot")<0){
				throw new NumberFormatException("La versión del entregable (${version}) no es válida; debe tener el siguiente formato: X.X.X.X-SNAPSHOT")
			}
		}
		File out = new File("${parentWorkspace}/version.txt")
		if (out.exists()) {
			assert out.delete()
			assert out.createNewFile()
		}
		out << "version=\"${version}\"\n"
		out << "groupId=\"${groupId}\""
		log.log "version.txt -->"
		out.eachLine {log.log it}
		log.log "version.txt --!"
	}

	LogUtils log = null;

	def initLogger(printer) {
		log = new LogUtils(printer);
	}

	def getTecnology(parentWorkspace){
		def tecnology = "notfound"

		for (def tec in tecnologias){
			log.log "Probando ${tec.key}..."
			def fichero = this.getRootFile(parentWorkspace,tec.value)
			if (fichero!=null && fichero.exists()){
				tecnology = tec.key
				break
			}
		}
		return tecnology
	}

	def getChangedfile(parentWorkspace){
		def changed = new File("${parentWorkspace}/changed.txt")
		if (changed.exists()) {
			assert changed.delete()
			assert changed.createNewFile()
		}
		return changed
	}

	/**
	 * Intenta resolver los valores posibles de la versión
	 * de un pom a una String, remontándose hasta baseDirectory si es necesario.
	 * En el peor caso:
	 ...
	 <version>${core-version}</version>
	 ...
	 <properties>
	 <toolkit-version>21.0.0</toolkit-version>
	 <core-version>${toolkit-version}-SNAPSHOT</core-version>
	 </properties>
	 Debería resolver la versión a:
	 21.0.0-SNAPSHOT
	 */
	def solveRecursive(File baseDirectory, pom, s) {
		StringBuilder sb = new StringBuilder();
		List<String> tokens = parse(s);
		for(String token: tokens) {
			if (token.startsWith('${') && token.endsWith('}')) {
				// Inmersión recursiva (resolviendo antes la propiedad)
				sb.append(solve(pom, lookupPropertyRecursive(baseDirectory,
						pom, token.substring(2, token.length() - 1))));
			}
			else {
				// Caso trivial
				sb.append(token);
			}
		}
		return sb.toString();
	}

	/**
	 * Intenta resolver los valores posibles de la versión
	 * de un pom a una String. En el peor caso:
	 ...
	 <version>${core-version}</version>
	 ...
	 <properties>
	 <toolkit-version>21.0.0</toolkit-version>
	 <core-version>${toolkit-version}-SNAPSHOT</core-version>
	 </properties>
	 Debería resolver la versión a:
	 21.0.0-SNAPSHOT
	 */
	def solve(pom, s) {
		StringBuilder sb = new StringBuilder();
		List<String> tokens = parse(s);
		for(String token: tokens) {
			if (token.startsWith('${') && token.endsWith('}')) {
				// Inmersión recursiva (resolviendo antes la propiedad)
				sb.append(solve(pom, lookupProperty(pom, token.substring(2, token.length() - 1))));
			}
			else {
				// Caso trivial
				sb.append(token);
			}
		}
		return sb.toString();
	}

	/**
	 * Intenta resolver una propiedad contra el elemento <properties/>
	 * de un pom.xml
	 * @param baseDirectory Directorio hasta el que se remonta intentando resolver
	 * 	la propiedad
	 * @param pom Estructura xml del pom en el que nos encontramos
	 * @param value Propiedad que intenta resolver
	 */
	def lookupPropertyRecursive(File baseDirectory, pom, value) {
		String ret = '';
		def isNull = { String s ->
			return s == null || s.trim().length() == 0;
		}
		groovy.util.Node projectPom = lastAncestor(pom);
		// Caso trivial
		def solveProjectParentVersion = { groovy.util.Node project ->
			if (project.parent != null && project.parent.version != null
			&& !isNull(project.parent.version.text())) {
				ret = solveRecursive(baseDirectory, project, project.parent.version.text());
			}
		}
		if (value == "project.version") {
			// Puede estar en el propio elemento
			if (projectPom.version != null && !isNull(projectPom.version.text())) {
				ret = solveRecursive(baseDirectory, projectPom, projectPom.version.text())
			}
			else {
				// Entonces el parent version
				solveProjectParentVersion(projectPom);
			}
		}
		else if (value == "project.parent.version") {
			solveProjectParentVersion(projectPom);
		}
		else {
			// Poblar el mapa de propiedades
			String tmp = lookupProperty(pom, value);
			if (!isNull(tmp) && !tmp.startsWith('${')) {
				ret = tmp;
			}
			else {
				Map<String, String> properties = populatePropertiesMap(baseDirectory, pom);
				ret = properties[value];
			}
		}
		return ret;
	}

	// Dado un nodo, nos retrotrae al ancestro final (el project en el caso de maven)
	private groovy.util.Node lastAncestor(groovy.util.Node node) {
		groovy.util.Node ret = node;
		boolean keepOn = true;
		while (keepOn) {
			if (ret.parent() == null) {
				keepOn = false;
			}
			else {
				ret = ret.parent()
			}
		}
		return ret;
	}

	// Construye una tabla de propiedades desde la raíz hasta el pom indicado
	private Map<String, String> populatePropertiesMap(File baseDirectory, pom) {
		Map<String, String> ret = new HashMap<String, String>();
		PomTree tree = null;
		if (propertiesCache[baseDirectory] != null) {
			tree = propertiesCache[baseDirectory];
		}
		else {
			tree = new PomTree(baseDirectory);
			propertiesCache.put(baseDirectory, tree);
		}
		MavenCoordinates soughtCoordinates = MavenCoordinates.readPom(pom);
		PomNode actual = null;
		for (Iterator<PomNode> iterator = tree.widthIterator();
		iterator.hasNext() && actual == null;) {
			// Buscar el nodo
			PomNode tmp = iterator.next();
			MavenCoordinates tmpCoordinates = tmp.getCoordinates();
			if (soughtCoordinates.equals(tmpCoordinates)) {
				actual = tmp;
			}
		}
		if (actual != null) {
			// Recorrer hacia arriba
			Deque<PomNode> nodes = new LinkedList<PomNode>();
			PomNode n = actual;
			nodes << n;
			while (n.getParent() != null) {
				nodes.push(n.getParent());
				n = n.getParent();
			}
			// Ahora descabezar la cola, empezando por el padre
			while(nodes.size() > 0) {
				PomNode node = nodes.pop();
				for (String key: node.getProperties().keySet()) {
					ret[key] = node.getProperties()[key];
				}
			}
		}
		return ret;
	}

	/**
	 * Intenta resolver una propiedad contra el elemento <properties/>
	 * de un pom.xml
	 */
	def lookupProperty(pom, value) {
		String ret = '';
		def isNull = { String s ->
			return s == null || s.trim().length() == 0;
		}
		// Caso trivial
		if (value == "project.version") {
			// Puede estar en el propio elemento
			if (pom.version != null && !isNull(pom.version.text())) {
				ret = solve(pom, pom.version.text())
			}
			else {
				// Entonces el parent version
				if (pom.parent != null && pom.parent.version != null
				&& !isNull(pom.parent.version.text())) {
					ret = solve(pom, pom.parent.version.text());
				}

			}
		}
		else if (pom.properties != null && pom.properties.size() > 0) {
			pom.properties[0].children().each {
				if ((it.name() instanceof groovy.xml.QName) && it.name().localPart == value) {
					ret = it.text();
				}
				else if ((it.name() instanceof java.lang.String) && it.name() == value) {
					ret = it.text();
				}
			}
		}
		return ret;
	}

	/**
	 * Cambia el valor de una propiedad en un pom.xml
	 */
	def setProperty(pom, property, value) {
		if (pom.properties != null && pom.properties.size() > 0) {
			pom.properties[0].children().each {
				if ((it.name() instanceof groovy.xml.QName) && it.name().localPart == property) {
					it.setValue(value);
				}
				else if (it.name() == property) {
					it.setValue(value);
				}
			}
		}
	}

	/**
	 * Parsea una cadena en sus componentes, separando propiedades
	 * Por ejemplo:
	 * 'aaa' -> ['aaa']
	 * 'asldkf${pom-variable}B' -> ['asldkf', '${pom-variable}', 'B']
	 * '${pom-variable-1}-${pom-variable2}-${pom-variable3}' -> ['${pom-variable-1}', '-', '${pom-variable-2}', '-', '${pom-variable-3}']
	 * '${pom-variable}-SNAPSHOT' -> ['${pom-variable}', '-SNAPSHOT']
	 */
	def parse(String s) {
		List<String> ret = null;
		if (s != null) {
			ret = new LinkedList<String>();
			int counter = 0;
			while (counter < s.length()) {
				int forward = counter;
				if (s.charAt(counter) == '$') {
					// Variable
					while(forward < s.length() && s.charAt(forward) != '}') {
						forward++;
					}
					// Consumir el último '}'
					forward++;
				}
				else {
					while (forward < s.length() && s.charAt(forward) != '$') {
						forward++;
					}
				}
				ret.add(s.substring(counter, forward));
				counter = forward;
			}
		}
		return ret;
	}

	/**
	 * Crea un archivo artifactsJson con los componentes filtrados por el parámetro componentsFilter.
	 * Si este parámetro llega vacío o a null se incluyen todos los componentes.
	 * @param artifactsComp
	 * @param home
	 * @return
	 */
	def writeJsonArtifactsMaven(artifactsComp, File home, String fileName, List<String> componentsFilter) {		
			def file = new File("${home.canonicalPath}/${fileName}")
			file.delete()
			file << "["
			
			if(componentsFilter == null || componentsFilter.size() == 0) {
				componentsFilter = [];
				artifactsComp.keySet().each { String compo ->
					componentsFilter.add(compo);
				}
			}		
			StringBuilder buffer_components = new StringBuilder()
			artifactsComp.each { artsComp ->
				if(componentsFilter.contains(artsComp.key.trim())) {
					def component = artsComp.key
					def artifacts = artsComp.value
					StringBuilder buffer_artifacts = new StringBuilder()
					if (artifacts.size() > 0) {
						artifacts.each { artifact ->
							buffer_artifacts.append("{\"version\":\"${artifact.version}\",\"component\":\"${component}\",\"groupId\":\"${artifact.groupId}\",\"artifactId\":\"${artifact.artifactId}\"},")
						}
						buffer_components.append(buffer_artifacts.substring(0, buffer_artifacts.length() - 1))
						buffer_components.append(",")
					}
				}
			}
	
			file << buffer_components.substring(0, buffer_components.length() - 1)
			file << "]"
		} 
}