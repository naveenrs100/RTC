package es.eci.utils


import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.Charset

import javax.xml.bind.DatatypeConverter

import es.eci.utils.base.Loggable
import es.eci.utils.commandline.CommandLineHelper
import es.eci.utils.pom.MavenCoordinates

/**
 * Métodos de utilidad para interactuar con nexus
 */
class NexusHelper extends Loggable {
	
	//---------------------------------------------------------------------------------
	// Constantes de la clase
		
	// Raíz de la URL en la vista de grupos de Nexus 
	private static final String CONTENT_GROUPS = "/content/groups"
	// Raíz de la URL en el servicio de repositorios de Nexus
	private static final String SERVICE_LOCAL_REPOSITORIES = "/service/local/repositories"
	// Raíz de la URL en el servicio de repositorios de Nexus
	private static final String CONTENT_REPOSITORIES = "/content/repositories"
	
	//---------------------------------------------------------------------------------
	// Propiedades de la clase
	
	// URL base del servidor nexus (sin grupos, repo, etc.)
	private String nexusBaseURL;
	
	// Opcionales para conectar a repositorios privados
	private String nexus_user;
	private String nexus_pass;
	
	//---------------------------------------------------------------------------------
	// Métodos de la clase

	/**
	 * Descarga de un fichero de nexus a partir de una URL
	 * @param groupId Coordenadas GAV de nexus: id. de grupo
	 * @param artifactId Coordenadas GAV de nexus: id. de artefacto
	 * @param version Coordenadas GAV de nexus: versión
	 * @param pathDescargaLibrerias Directorio de descarga
	 * @param extension Extensión del fichero a descargar
	 * @param pathNexus URL del repositorio nexus
	 */
	@Deprecated
	public static File downloadLibraries(String groupId, String artifactId, String version, String pathDescargaLibrerias, String extension, String pathNexus) {
		def fixExtension =  (extension.startsWith(".")?extension:("."+extension));
		def pathNexusFinal = "";
		pathNexusFinal = pathNexus + (pathNexus.endsWith("/")?"":"/") +
			groupId.replaceAll("\\.", "/") + "/" + artifactId + "/" + 
			version + "/" + artifactId + "-" + version + fixExtension;

		println pathNexusFinal;
		URL urlNExus = new URL(pathNexusFinal);
		//java.net.Proxy proxyApl = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("proxycorp.geci", 8080))
		ReadableByteChannel lectorEci = 
			Channels.newChannel(urlNExus.openConnection().getInputStream());
		//ReadableByteChannel lectorEciRelease = Channels.newChannel(urlNExus.openConnection().getInputStream());
		println "pathDescargaLibrerias:"+pathDescargaLibrerias
		File destino = new File(pathDescargaLibrerias+"/"+artifactId+fixExtension)
		FileOutputStream fos = new FileOutputStream(destino);
		fos.getChannel().transferFrom(lectorEci, 0, 1 << 24);
		println "To local file system path:"+pathDescargaLibrerias+"/"+artifactId+fixExtension
		return destino
	}

	/**
	 * Sube a Nexus un entregable por medio de maven
	 * @param maven Ruta del entregable de maven
	 * @param groupId Coordenadas GAV de nexus: id. de grupo
	 * @param artifactId Coordenadas GAV de nexus: id. de artefacto
	 * @param version Coordenadas GAV de nexus: versión
	 * @param rutaFichero Ruta completa del fichero a subir
	 * @param pathNexus URL de nexus
	 * @param pathNexus URL del repositorio nexus
	 * @param tipo Tipo de fichero (jar/zip/tar/etc.)
	 */
	
	public static int uploadToNexus(String maven, String groupId, String artifactId, String version, String rutaFichero, String pathNexus, String tipo, Closure log = null, String uDeployUser = null, String uDeployPass = null) {
		String repo = pathNexus.split('/')[pathNexus.split('/').length - 1]
		String local_folder = rutaFichero.substring(0,rutaFichero.lastIndexOf(System.getProperty("file.separator")) )
		
		def comando = [
			maven,
			"deploy:deploy-file",
			"-DgroupId=" + groupId,
			"-DartifactId=" + artifactId,
			"-Dversion=" + version,
			"-U",
			"-Dpackaging=${tipo}",
			"-Dfile=" + rutaFichero,
			"-Durl=${pathNexus}",
			"-DrepositoryId=${repo}"
		]
		
		if ( (uDeployUser != null) && (uDeployPass != null) ) {
			comando.add("-DDEPLOYMENT_USER=" + uDeployUser)
			comando.add("-DDEPLOYMENT_PWD=" + uDeployPass)
		}
		
		def exec_command = comando.join(" ")
		
		println "Comando :${exec_command}"
		
		CommandLineHelper buildCommandLineHelper = new CommandLineHelper(exec_command);
		buildCommandLineHelper.initLogger(log == null ? { println it} : log);
		
		int returnCode = buildCommandLineHelper.execute(new File(local_folder));
				
		if( returnCode != 0) {
			println ("Error al ejecutar comando ${exec_command} . Código -> ${returnCode}");
		}
		return returnCode
	}

	/**
	 * Sube a Nexus un comprimido por medio de maven
	 * @param uDeployUser Coordenadas GAV de nexus: id. de grupo
	 * @param uDeployPass Coordenadas GAV de nexus: id. de grupo
	 * @param m2home ruta inslación Maven
	 * @param groupId Coordenadas GAV de nexus: id. de grupo
	 * @param artifactId Coordenadas GAV de nexus: id. de artefacto
	 * @param repositoryId
	 * @param version Coordenadas GAV de nexus: versión
	 * @param rutaFichero Ruta completa del fichero a subir
	 * @param pathNexus URL de nexus
	 * @param tipo Tipo de fichero (jar/zip/tar/etc.)
	 * @param isRelease indica se es release o no
	 */
	public static void uploadTarNexusMaven(String uDeployUser, String uDeployPass, String m2home, String groupId, String artifactId, String repositoryId, String version, String rutaFichero, String pathNexus, String tipo, String isRelease, Closure log = null) {

		if (isRelease == "false" && !version.contains("-SNAPSHOT")){
			println "******* Se anade el snapshot a la version ******"
			version += "-SNAPSHOT"
		}

		m2home=m2home+"/bin"
		def cadena = "mvn deploy:deploy-file -Durl=" + pathNexus + " -DrepositoryId=eci-c-snapshots -Dfile=" + rutaFichero + " -DgroupId=" + groupId + " -DartifactId=" + artifactId + " -Dversion=" + version + " -Dtype=" + tipo + " -DuniqueVersion=false -DDEPLOYMENT_USER=" + uDeployUser + " -DDEPLOYMENT_PWD=" + uDeployPass

		def p = null
		if (System.properties['os.name'].toLowerCase().contains('windows')) {
			p = ['cmd.exe' , '/C' , cadena].execute(null, new File(m2home))
		}else {
			p = ['sh' , '-c' , cadena].execute(null, new File(m2home))
		}

		log cadena

		StreamGobbler cout = new StreamGobbler(p.getInputStream(), true)
		StreamGobbler cerr = new StreamGobbler(p.getErrorStream(),true)
		cout.start()
		cerr.start()
		p.waitFor()

		log "Salida: " + cout.getOut()
		log "Salida error: " + cerr.getOut()
	}

	/**
	 * Sube un comprimido a Nexus con binarios.
	 * @param uDeployUser Coordenadas GAV de nexus: id. de grupo
	 * @param uDeployPass Coordenadas GAV de nexus: id. de grupo
	 * @param gradleBin Binario de gradle
	 * @param cScriptsHome Home del script store de C
	 * @param nexusPublicC Coordenadas GAV de nexus: id. de grupo
	 * @param groupId Coordenadas GAV de nexus: id. de grupo
	 * @param artifactId Coordenadas GAV de nexus: id. de artefacto
	 * @param version Coordenadas GAV de nexus: versión
	 * @param pathNexus URL del repositorio nexus
	 * @param isRelease Cadena 'true' o 'false'
	 * @param artifactPath Path del artefacto a subir
	 * @param artifactType tar/zip/jar...
	 */
	public static int uploadTarNexus(uDeployUser, uDeployPass, gradleBin, cScriptsHome, nexusPublicC, String groupId, String artifactId, String version, String pathNexus, String isRelease, String artifactPath, String artifactType, Closure log = null){
		if (isRelease == "false" && !version.contains("-SNAPSHOT")){
			println "******* Se anade el snapshot a la version ******"
			version += "-SNAPSHOT"
		}
		def nexusGradleUploadC="${cScriptsHome}C_workFlowNexus/uploadProjectsC.gradle"

		groupId = groupId.replaceAll(/\(/,"")
		groupId = groupId.replaceAll(/\)/,"")



		if (log != null) {
			log "gradleBin:${gradleBin}\n"+
					"nexusGradleUploadC:${nexusGradleUploadC}\n"+
					"artifactPath:${artifactPath}\n"+
					"artifactId:${artifactId}\n"+
					"artifactType:${artifactType}\n"+
					"groupId:${groupId}\n"+
					"version:${version}\n"+
					"nexusPublicC:${nexusPublicC}\n"+
					"nexusUploadRepoC:${pathNexus}\n"
		}

		def uploadCNexus = []
		uploadCNexus.add("${gradleBin}")
		uploadCNexus.add("-i")
		uploadCNexus.add("-b${nexusGradleUploadC}")
		uploadCNexus.add("uploadCartifact")
		uploadCNexus.add("-PartifactPath=${artifactPath}")
		uploadCNexus.add("-PartifactId=${artifactId}")
		uploadCNexus.add("-PartifactType=${artifactType}")
		uploadCNexus.add("-PgroupId=${groupId}")
		uploadCNexus.add("-Pversion=${version}")
		uploadCNexus.add("-PnexusPlubicC=${nexusPublicC}")
		uploadCNexus.add("-PnexusUploadC=${pathNexus}")
		uploadCNexus.add("-Puser=${uDeployUser}")
		uploadCNexus.add("-Ppassword=${uDeployPass}")
		//uploadCNexus.add(uDeployUser)
		//uploadCNexus.add(uDeployPass)
		if (log != null) {
			log uploadCNexus.join(" ")
		}
		def procNexus = uploadCNexus.execute()
		procNexus.waitFor()
		if (procNexus.exitValue() == 0){
			if (log != null) {
				log "Upload Nexus C: CORRECTO"
				log procNexus.in.text
			}
		}else{
			if (log != null) {
				log "Upload Nexus C: ERROR"
				log procNexus.in.text
				log procNexus.err.text
			}
		}
		return procNexus.exitValue()
	}
	
	/** 
	 * Construye una instancia del helper con la URL de nexus apropiada
	 * @param nexusURL URL base de Nexus o bien del repo public de nexus
	 */
	public NexusHelper(String nexusURL) {
		// Trata de deducir la URL base de nexus
		if (nexusURL.contains(CONTENT_GROUPS)) {
			this.nexusBaseURL = 
				nexusURL.substring(0, 
					nexusURL.indexOf(CONTENT_GROUPS));
		}
		else if (nexusURL.contains(CONTENT_REPOSITORIES)) {
			this.nexusBaseURL = 
				nexusURL.substring(0, 
					nexusURL.indexOf(CONTENT_REPOSITORIES));
		}
		else if (nexusURL.contains(SERVICE_LOCAL_REPOSITORIES)) {
			this.nexusBaseURL = 
				nexusURL.substring(0, 
					nexusURL.indexOf(SERVICE_LOCAL_REPOSITORIES));
		}
		else {
			this.nexusBaseURL = nexusURL;
		}
	}
	
	/**
	 * Devuelve la información completa de resolución de unas coordenadas contra Nexus.
	 * @param coordinates GAV + packaging del artefacto
	 * @return Información completa devuelta por el servicio de resolución de Nexus
	 */
	public String getResolveInformation(MavenCoordinates coordinates) {
		String content = null;
		long millis = Stopwatch.watch {
			def groupId = coordinates.getGroupId();
			def artifactId = coordinates.getArtifactId();
			def version = coordinates.getVersion();
			def packaging = coordinates.getPackaging();
			def repository = coordinates.getRepository();
			if (StringUtil.isNull(nexusBaseURL)
					|| StringUtil.isNull(groupId) 
					|| StringUtil.isNull(artifactId) 
					|| StringUtil.isNull(version) 
					|| StringUtil.isNull(packaging)) {
				throw new NullPointerException(
					"""Nexus -> $nexusBaseURL ;; 
					Repo -> $repository ;; 
					GAV -> $groupId :: $artifactId :: $version ;; 
					Packaging -> $packaging""");
			}
			String resolverURL = nexusBaseURL + 
				"/service/local/artifact/maven/resolve?r=${repository}&g=${groupId}&a=${artifactId}&v=${version}&p=${packaging}";
			String classifier = coordinates.getClassifier(); 
			if (classifier != null && classifier.trim().length() > 0) {
				resolverURL += "&c=${classifier}"
			}
			
			log "Resolviendo el artefacto contra $resolverURL";
			ReadableByteChannel lectorEci = getByteChannel(resolverURL, repository);
			ByteBuffer bb = ByteBuffer.allocate(1024);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			boolean keepOn = true;
			while (keepOn) {
				int readBytes = lectorEci.read(bb);
				if (readBytes == -1) {
					keepOn = false;
				}
				else {
					bb.rewind();
					byte[] tmp = new byte[readBytes];
					bb.get(tmp);
					baos.write(tmp);
				}
			}
			// Traducir baos a una cadena
			content = new String(baos.toByteArray(), Charset.forName("UTF-8"));
		}
		log "Resolución de artefacto -> $millis mseg."
		log content;
		return content;
	}
	
	/**
	 * Resuelve el timestamp exacto de un artefacto Snapshot contra Nexus.
	 * @param coordinates Coordenadas maven del artefacto
	 * @param repository Nombre del repositorio de Nexus
	 * @return Si la versión acaba en -SNAPSHOT, devuelve el timestamp del último
	 * snapshot de ese grupo y artefacto.  En caso contrario, devuelve la versión.
	 */
	public String resolveSnapshot(MavenCoordinates coordinates) {
		String ret = coordinates.getVersion();
		if (coordinates.getVersion().endsWith("-SNAPSHOT")) {
			String content = getResolveInformation(coordinates);
			// El contenido se parsea a XML
			def result = new XmlSlurper().parseText(content);
			ret = result.data[0].version[0];
		}
		return ret;
	}
	
	/**
	 * Resuelve la dirección exacta de descarga de un artefacto contra Nexus.
	 * @param coordinates Coordenadas maven del artefacto
	 * @param repository Nombre del repositorio de Nexus
	 * @return Dirección exacta del artefacto a partir de nexusBaseURL
	 */
	public String resolveDownloadLink(MavenCoordinates coordinates) {
		String content = getResolveInformation(coordinates);
		// El contenido se parsea a XML
		def result = new XmlSlurper().parseText(content);
		String url = result.data[0].repositoryPath[0];
		return "${nexusBaseURL}/service/local/repositories/${coordinates.repository}/content${url}"
	}
	
	/**
	 * Descarga de un fichero de nexus a partir de una URL
	 * @param coordinates Coordenadas Maven del fichero
	 * @param downloadPath Directorio de descarga
	 * @param pathNexus URL del repositorio nexus
	 */
	public File download(MavenCoordinates coordinates, File downloadPath) {
		String fixExtension = 
			(coordinates.getPackaging().startsWith(".")?
				coordinates.getPackaging():("." + coordinates.getPackaging()));
		String pathNexusFinal = resolveDownloadLink(coordinates);

		File target = null;
		long millis = Stopwatch.watch {
			log "Descargando de Nexus: $pathNexusFinal"
			def repository = coordinates.getRepository();
			ReadableByteChannel reader = getByteChannel(pathNexusFinal, repository);
			target = new File(downloadPath, coordinates.getArtifactId() + fixExtension)
			FileOutputStream fos = new FileOutputStream(target);
			// 1 << 30 -> 1 gigabyte de límite
			fos.getChannel().transferFrom(reader, 0, 1 << 30);
		}
		log "Descarga de artefacto -> $millis mseg."
		log "Ruta de la descarga: " +
			downloadPath.getCanonicalPath() + "/" + 
			coordinates.getArtifactId() + fixExtension
		return target;
	}
	
	/**
	 * Este método sube un fichero a nexus usando la API REST 
	 * @param coordinates Coordenadas a subir
	 * @param file Fichero a subir
	 */
	public void upload(MavenCoordinates coordinates, File file) {
		long millis = Stopwatch.watch {
			MultipartUtility mpu = new MultipartUtility(
				"${nexusBaseURL}/service/local/artifact/maven/content",
				'UTF-8', nexus_user, nexus_pass)
			
			mpu.addFormField('r', coordinates.getRepository())
			mpu.addFormField('hasPom', 'false')
			mpu.addFormField('g', coordinates.getGroupId())
			mpu.addFormField('a', coordinates.getArtifactId())
			mpu.addFormField('v', coordinates.getVersion())
			if (StringUtil.notNull(coordinates.getClassifier())) {
				mpu.addFormField('c', coordinates.getClassifier())
			}
			mpu.addFormField('e', coordinates.getPackaging())
			mpu.addFormField('p', coordinates.getPackaging())
			
			mpu.addFilePart('file', file)
			
			log mpu.finish()
		}
		log "Subida de artefacto -> $millis mseg."
	}
	
	// Obtiene un canal de datos asegurado con usuario y password si
	//	fuera necesario
	private ReadableByteChannel getByteChannel(String url, String repository) {
		URL resolverService = new URL(url);
		URLConnection uc = resolverService.openConnection();
		
		// Estamos en repo privado
		if (repository != "public") {
			if ( StringUtil.isNull(nexus_user) || StringUtil.isNull(nexus_pass) ) {
				log "### Aviso, no se están informando las credenciales para conectar a un repositorio privado" 
			} 
			else {
				String userPassword = nexus_user + ":" + nexus_pass
				String encoding = DatatypeConverter.printBase64Binary(userPassword.getBytes());
				uc.setRequestProperty ("Authorization", "Basic " + encoding);
			}
		}
		return Channels.newChannel(uc.getInputStream());
	}
	
	/**
	 * @return the nexus_user
	 */
	public String getNexus_user() {
		return nexus_user;
	}

	/**
	 * @param nexus_user the nexus_user to set
	 */
	public void setNexus_user(String nexus_user) {
		this.nexus_user = nexus_user;
	}

	/**
	 * @return the nexus_pass
	 */
	public String getNexus_pass() {
		return nexus_pass;
	}

	/**
	 * @param nexus_pass the nexus_pass to set
	 */
	public void setNexus_pass(String nexus_pass) {
		this.nexus_pass = nexus_pass;
	}

}