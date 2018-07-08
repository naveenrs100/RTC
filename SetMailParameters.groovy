package es.eci.utils

import hudson.model.*

import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit;

import buildtree.BuildBean;
import buildtree.BuildTreeHelper

import com.deluan.jenkins.plugins.rtc.changelog.*

import es.eci.utils.base.Loggable

class SetMailParameters extends Loggable {

	def params = []
	File ficheroChangeLog = null
	File releaseNotesFile = null
	
	def tablaResultados = new HashMap()
	
	public SetMailParameters() {	
		tablaResultados.put(Result.SUCCESS,"&Eacute;XITO")
		tablaResultados.put(Result.FAILURE,"FALLO")
		tablaResultados.put(Result.UNSTABLE,"INESTABLE")
		tablaResultados.put(Result.ABORTED,"ABORTADO")
		tablaResultados.put(Result.NOT_BUILT,"OMITIDO")
	}

	// Descompone una lista de destinatarios separada por comas en una lista de
	//	cadenas de caracteres
	private List<String> parseReceivers(String managersMail) {
		List<String> ret = new LinkedList<String>()
		def managers = managersMail.split(',')
		managers.each { if (it != null && it.trim().size() > 0) { ret << it } }
		return ret;
	}
	
	/**
	 * Este método crea en el contexto de la acción las variables necesarias 
	 * para el envío de la notificación por correo: destinatarios, cuerpo del
	 * correo, etc. 
	 * 
	 * Si el método determinara que no es necesario enviar el mail, asignará
	 * un valor falso a la variable sendMail del build jobInvoker.
	 * 
	 * @param numeroLineas Número de líneas del log a 
	 * 	incluir en el correo 
	 * @param causa Causa de la llamada al build.
	 * @param action build/deploy/release/addFix/addHotfix/GenerateReleaseNotes
	 * @param build Instancia de la ejecución en jenkins
	 * @param managersMail Cadena con las direcciones de correo 
	 * 	de los destinatarios, separadas por comas
	 * @param userRTC Nombre de usuario funcional de RTC
	 * @param mailSubject Tema del correo
	 * @param defaultManagersMail Cadena con las direcciones de 
	 * 	correo obligatorias de los destintarios del equipo de IC,
	 * 	separadas por comas
	 */
	def setParameters (
			int numeroLineas,
			Cause causa,
			String action,
			AbstractBuild build,
			String managersMail,
			String userRTC,
			String mailSubject,
			String defaultManagersMail = null) {
		if (causa != null || action == "GenerateReleaseNotes"){
			// Ejecución -----------
			def nombrePadre = causa.getUpstreamProject()
			def numeroPadre = causa.getUpstreamBuild()
			def buildInvoker = Hudson.instance.getJob(nombrePadre).getBuildByNumber(Integer.valueOf(numeroPadre))
			log "Acciones del buildInvoker: $nombrePadre"
			
			log "**** ACTION: " + action
			// Añadir a la lista de destinatarios los indicados expresamente en el job
			List<String> receivers = parseReceivers(managersMail)
			if (causa != null){
				JobRootFinder finder = new JobRootFinder(buildInvoker);
				finder.initLogger(this);
				AbstractBuild ancestor = finder.getRoot();
				if (ancestor.getProject().getName().contains("-COMP-")) {
					log "Ancestro: job de componente"
					// Componer el correo de componente
					prepareData(buildInvoker,build,userRTC, receivers)
					prepareDataReleaseNotes(buildInvoker, build, userRTC)	
				}
				else {
					log "Ancestro: job de grupo"
					// Si el ancestro es de grupo/corriente, y estamos en el componente, no
					//	hacemos nada
					if (!buildInvoker.getProject().getName().contains("-COMP-")) {
						log "Actual: job de grupo"
						// Preparar el correo de grupo/corriente
						prepareData(buildInvoker,build,userRTC, receivers)
						prepareDataReleaseNotes(buildInvoker, build, userRTC)		
					}
					else {						
						log "Actual: job de componente"
						ParamsHelper.deleteParams(build, 'sendMail')
						ParamsHelper.addParams(build, ['sendMail':'false'])
					}
				}
				// Añadir a la lista de destinatarios la lista de destinatarios por defecto
				//	indicados en la variable de entorno MANAGERS_MAIL
				addDefaultManagersMail(defaultManagersMail, receivers)			
			}

			if (action == "GenerateReleaseNotes"){
				prepareDataReleaseNotes(buildInvoker,build,userRTC)
			}
			
			log "Lista de correos 2: $receivers"
			writeResult (buildInvoker,numeroLineas,mailSubject,build, receivers)

		}else{
			log "ESTE JOB NECESITA SER LLAMADO SIEMPRE DESDE OTRO!!"
			build.setResult(Result.FAILURE)
		}

	}
	
	// Añade la lista de managers por defecto a la lista de destinatarios
	def private addDefaultManagersMail(String defaultManagersMail, List destinatarios) {
		if (defaultManagersMail != null) {
			log "Añadiendo los managers por defecto..."
			def arrayDefaultManagers = defaultManagersMail.split(",")
			arrayDefaultManagers.each { defaultManager ->
				if (!destinatarios.contains(defaultManager)) {
					log "Añadiendo ${defaultManager}..."
					destinatarios.add(defaultManager)
				}
			}
		}
	}

	// Recoge los datos de cambios en RTC para componer el correo
	def private prepareData(buildInvoker,build,userRTC, List destinatarios){

		for (Action a : buildInvoker.getActions()) {
			log a.getClass().getName()
		}
		ficheroChangeLog = new File("${buildInvoker.getRootDir()}/changelog.xml")
		def changeSet = getChangeSet(buildInvoker,ficheroChangeLog)
		log "Conjunto de cambios en ${buildInvoker.getRootDir()}/changelog.xml: $changeSet"
		
		if (changeSet != null && changeSet.isEmptySet()) {
			log "El conjunto de cambios existe pero está vacío"
		}
		if (changeSet!=null){
			log "buildInvoker.getRootDir(): ${buildInvoker.getRootDir()}"
			log "buildInvoker.getChangeSet: ${changeSet}"

			log "<<< LOG >>> [build : Class] ["+build+" : "+build.getClass()+"]"

			// Se mete el changeset en el build para que esté accesible desde el Jelly
			Field campo = AbstractBuild.getDeclaredField("changeSet")
			campo.setAccessible(true)
			campo.set(build, new WeakReference<JazzChangeSetList>(changeSet));

			build.setResult(buildInvoker.getResult())
			log "build.getChangeSet: ${build.getChangeSet()}"

			// Añade los correos de los autores de los cambios
			def unknownMails = ""
			def autores = []
			// Comprueba que los correos no hay ningún unknow
			changeSet.each() { change ->
				def mail = change.getEmail()
				def author = change.getUser()
				if (mail!=null && mail.indexOf("@")==-1 && autores.find{a->a==author}==null){
					if ("${author}"!="${userRTC}"){
						autores.add(author)
						unknownMails += "<li>${author}: ${mail}</li>"
					}
				}
				else {
					if (mail!=null && mail.indexOf("@") !=-1 && !destinatarios.contains(mail)) {
						destinatarios.add(mail)
					}
				}
				log "${author}: ${mail}"
			}

			if (unknownMails.length()>0){
				params.add(new StringParameterValue("unknownMails",unknownMails))
			}
			
			log "Destinatarios -->"
			destinatarios.each { log it }
			log "Lista de correos 1: $destinatarios"
		}
	}

	// Recoge el resultado de comparar las instantáneas/líneas base solicitadas
	//	en las release notes
	def private prepareDataReleaseNotes(buildInvoker,build,userRTC){
		// Generate release notes
		releaseNotesFile = new File("${buildInvoker.getRootDir()}/releaseNotesLog.xml")
		def releaseNotes = getChangeSet(buildInvoker,releaseNotesFile)
		log "Release notes en ${buildInvoker.getRootDir()}/releaseNotesLog.xml: $releaseNotes"
		if (releaseNotes != null && releaseNotes.isEmptySet()) {
			log "El conjunto de cambios existe pero está vacío"
		}
		if (releaseNotes!=null){
			log "buildInvoker.getRootDir(): ${buildInvoker.getRootDir()}"
			log "buildInvoker.getChangeSet: ${releaseNotes}"

			log "<<< LOG >>> [build : Class] ["+build+" : "+build.getClass()+"]"

			// Se mete el releaseNotes en el build para que esté accesible desde el Jelly
			Field campo = AbstractBuild.getDeclaredField("changeSet")
			campo.setAccessible(true)
			campo.set(build, new WeakReference<JazzChangeSetList>(releaseNotes));

			build.setResult(buildInvoker.getResult())
			log "build.getChangeSet: ${build.getChangeSet()}"
		}

	}

	/**
	 * Construye el resumen HTML de la ejecución de los pasos de una construcción.
	 * @param buildInvoker Ejecución en jenkins que nos interesa resumir (normalmente,
	 * 	un lanzamiento de componente)
	 * @param numeroLineas Líneas del log (desde la última) que se mostrarán en caso de error
	 * @param mailSubject Asunto del correo de notificación
	 * @param build Ejecución en jenkins del propio paso de envío de correo (a esta
	 * 	ejecución se añadirán parámetros con el resumen, etc.)
	 * @param destinatarios Lista de destinatarios del correo de notificación
	 */ 
	def private writeResult (buildInvoker,int numeroLineas,mailSubject, build, destinatarios){
		//----------
		// Introduce el resultado de la ejecución para mostrarlo en el correo

		def logBuild = getBuildLog(buildInvoker)
		def nombreHijo = buildInvoker.getEnvironment(null).get("LAST_TRIGGERED_JOB_NAME")
		def buildWorkFlow = getBuild(nombreHijo,logBuild)
		def noExecTxt = "NOT_EXECUTED"
			
		BuildTreeHelper helper = new BuildTreeHelper(numeroLineas);
		helper.initLogger(this);
		List<BuildBean> beans = null;
		long timeExecutionTree = Stopwatch.watch {
			beans = helper.executionTree(buildInvoker);
		}
		log "Construcción del árbol: ${timeExecutionTree} mseg."
		beans = helper.leafs(beans);

		def conWarning = false
		def resumenHTML = ""
		String statusHTML = ""
		
		beans.each { BuildBean buildPaso ->
			if (buildPaso.getResult() != Result.SUCCESS && buildPaso.getResult() != noExecTxt) {
				conWarning = true
			}
			resumenHTML = addHTML(resumenHTML, buildPaso, numeroLineas, true, false)
			statusHTML = addHTML(statusHTML, buildPaso, numeroLineas, false, true)
		}
		
		// Añade los parametros para ser pintados en el mail
		def status = tablaResultados[buildInvoker.getResult()]
		if (conWarning && status != tablaResultados[Result.FAILURE])
			status = status + " con AVISOS"
		status = status.replace('&Eacute;', 'E')
		params.add(new StringParameterValue("MAIL_BUILD_STATUS",status))
		params.add(new StringParameterValue("MAIL_LIST", destinatarios.join(',')))
		log "MAIL_LIST: $destinatarios"
		params.add(new StringParameterValue("duration",buildInvoker.getDurationString()))
		params.add(new StringParameterValue("urlCheckin",getUrlCheckin(buildInvoker)))
		params.add(new StringParameterValue("version",getVersion(buildInvoker)))
		params.add(new StringParameterValue("statusHTML",statusHTML))
		params.add(new StringParameterValue("resumenHTML",resumenHTML))
		params.add(new StringParameterValue("resultadoTraducido",tablaResultados[buildInvoker.getResult()]))
		if (ficheroChangeLog != null)
			params.add(new StringParameterValue("rutaFicheroChangeLog",ficheroChangeLog.getCanonicalPath()))
		if (releaseNotesFile != null) {
			log("Preparando la ruta del fichero de release notes: " + releaseNotesFile.getCanonicalPath());
			params.add(new StringParameterValue("rutaFicheroReleaseNotesLog",releaseNotesFile.getCanonicalPath()))
		}
		else {
			log "No hay fichero de release notes"
		}
		if (mailSubject==null || mailSubject.length()==0)
			params.add(new StringParameterValue("MAIL_SUBJECT",buildInvoker.getProject().getName()))

		//introduce los nuevos parametros
		if (buildWorkFlow!=null){
			def paramsWorkflow = buildWorkFlow?.actions.find{ it instanceof ParametersAction }?.parameters
			params.addAll(paramsWorkflow)
		}
		setParams(build,params)
	}

	// Funciones --------
	// Closure que obtiene el job
	def getBuild = { nombreJob, texto ->
		print "${nombreJob} "
		def ret = null
		try {
			def matcher =  texto =~ /(?s).*${nombreJob}[^#]*#([0-9]+) completed.*/
			if (matcher.matches()){
				def buildNumber = matcher [0] [1]
				log ": ${buildNumber}"
				ret = Hudson.instance.getJob(nombreJob).getBuildByNumber(Integer.valueOf(buildNumber))
			}
		}
		catch(Exception e) {
			log(e.getMessage());
			ret = null;
		}
		return ret
	}
	
	def setParams(build,params){
		def paramsIn = build?.actions.find{ it instanceof ParametersAction }?.parameters
		def index = build?.actions.findIndexOf{ it instanceof ParametersAction }
		def paramsTmp = []
		if (paramsIn!=null){
			//No se borra nada para compatibilidad hacia atrás.
			paramsTmp.addAll(paramsIn)
			//Borra de la lista los paramaterAction
			build?.actions.remove(index)
		}
		// Linea de depuracion
log "************************ DEPURACIÓN ********************************"
		paramsTmp.addAll(params)
		// Linea de depuracion
log "************************ DEPURACIÓN ********************************"
		build?.actions.add(new ParametersAction(paramsTmp))
		// Linea de depuracion
log "************************ DEPURACIÓN ********************************"
	}

	/**
	 * Añade líneas al resumen HTML destinado al correo de notificación.
	 * @param html Resumen HTML del correo de notificación
	 * @param build Bean con la información del paso de ejecución
	 * @param numeroLineas Líneas de profundidad a mostrar si hay un error
	 * @param enviaSiempre 
	 * @param addLog 
	 * @return
	 */
	def String addHTML (String html, BuildBean build, numeroLineas, boolean enviaSiempre, boolean addLog){
		def noExecTxt = "NOT_EXECUTED"
		
		def resultTmp = build!=null?build.getResult():noExecTxt
		def isError = resultTmp!=Result.SUCCESS && resultTmp!=noExecTxt
		def result = resultTmp
		if (tablaResultados[resultTmp] != null) {
			result = tablaResultados[resultTmp]
		}
		String duration = getDuration(build.duration);
		def escribe = enviaSiempre
		log "addHTML-> ${build.name} - ${build.description}: ${result} -> profundidad ${build.depth}"
		int depth = build.getDepth() - 2;
		String indentation = "";
		if (depth > 0) {
			indentation = "style=\"text-indent:${2*depth}em\"";
			log "Aplicando sangría: $indentation"
		}
		String htmlTmp = "<p ${indentation} class='${resultTmp}'><span>${build.description}:</span> ${result} ${duration}</p>"
		if (isError && addLog){
			htmlTmp += "<br/>Last ${numeroLineas} lines:<br/><br/>"
			build.getLogTail().each(){ htmlTmp += "${it}<br/>" }
			htmlTmp += "<br/>"
			escribe = true;
		}
		if (escribe) html += htmlTmp
		return html
	}
	
	// Construye una descripción de la duración legible
	private String getDuration(Long duration) {
		String ret = "";
		long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - 
		      TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration));
		if (minutes > 0) {
			ret = String.format("%d min %d seg", minutes, seconds);
		}
		else {
			ret = String.format("%d seg", seconds);
		}
		return ret;
	}

	// Devuelve la URL de las pizarras sobre el cuadro de mando de Checking
	//	definida en las propiedades  
	def getUrlCheckin(build){
		def pizarras = build.getEnvironment(null).get("CHECKING_PIZARRAS")
		log "Análisis estático: " + pizarras
		return pizarras
	}

	def getVersion(build){
		def version = new File("${build.workspace}/version.txt")
		log "Recuperando la versión de ${version.canonicalPath} ..."
		try {
			if (version.exists()) {
				def config = new ConfigSlurper().parse(version.toURL())
				def v = config.getProperty("version")
				return (v instanceof String?v:"")
			}
		}
		catch (Exception e) {
			log e.getMessage()
		}
		return ""
	}

	def getBuildLog (build){
		def res = ""
		if (build!=null)
			res = build.getLog()
		return res
	}

	def getChangeSet(build,changeLogFile){
		def ret = null
		try {
			if (changeLogFile!=null && changeLogFile.exists()){
				def jazzChangeLogReader = new JazzChangeLogReader()
				ret = jazzChangeLogReader.parse(build,changeLogFile)
			}
		}
		catch(Exception e) {
			// Error de parseo: ¿puede ser que el fichero no exista?
			// Nos lo tomamos como un warning y avanzamos
			log e.getMessage()
		}
		return ret;
	}


}